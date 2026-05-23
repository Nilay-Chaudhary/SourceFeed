package com.socialfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialfeed.dto.PostVerificationDto;
import com.socialfeed.dto.VerificationResult;
import com.socialfeed.event.PostCreatedEvent;
import com.socialfeed.model.AiVerdict;
import com.socialfeed.model.Post;
import com.socialfeed.model.PostVerification;
import com.socialfeed.repository.PostRepository;
import com.socialfeed.repository.PostVerificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationAgent {

    private static final int MAX_LOGGED_ASSISTANT_CHARS = 1500;

    private static final int MAX_INITIAL_SOURCES = 3;
    private static final int MAX_SELECTED_PASSAGES = 3;
    private static final int CLAIM_MAX_LENGTH = 500;
    private static final double WEAK_EVIDENCE_THRESHOLD = 0.3d;

    private static final int MAX_AGENT_TURNS = 4;
    private static final int MAX_EXTRA_CHUNKS = 3;
    private static final int MAX_CHUNK_TEXT_CHARS = 350;
    private static final int MAX_INITIAL_TOTAL_CHUNK_CHARS = 1500;
    private static final int MAX_TOTAL_CHUNK_CHARS = 2500;

    private final PostRepository postRepository;
    private final PostVerificationRepository postVerificationRepository;
    private final EvidenceFetcher evidenceFetcher;
    private final AiChatProvider aiChatProvider;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public void verifyPost(PostCreatedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = "success";
        AiVerdict verdict = AiVerdict.COULD_NOT_PROCESS;
        Post post = null;
        List<String> usedSources = List.of();

        try {
            if (event == null || event.getPostId() == null) {
                meterRegistry.counter("verification_failed_total", "reason", "invalid_event").increment();
                log.warn("Skipping verification because the event was missing a post id");
                status = "invalid_event";
                return;
            }

            log.info("Starting AI verification: postId={}, eventId={}, userId={}", event.getPostId(), event.getEventId(), event.getUserId());

            post = postRepository.findWithSourcesById(event.getPostId()).orElse(null);
            if (post == null) {
                meterRegistry.counter("verification_failed_total", "reason", "missing_post").increment();
                log.warn("Skipping verification because the post was not found: postId={}", event.getPostId());
                status = "missing_post";
                return;
            }

            log.info(
                "Loaded post for verification: postId={}, contentChars={}, sourceCount={}, aiVerdict={}",
                post.getId(),
                post.getContent() == null ? 0 : post.getContent().length(),
                post.getSources() == null ? 0 : post.getSources().size(),
                post.getAiVerdict()
            );

            VerificationContext context = buildContext(post);
            usedSources = context.sourcesUsed();
            log.info(
                "Built verification context: postId={}, claimChars={}, sourcesUsed={}",
                post.getId(),
                context.claim().length(),
                usedSources.size()
            );

            // Build structured chunks format for /v1/verify endpoint instead of message history
            StructuredChunkPlan chunkPlan = buildStructuredChunkPlan(post);
            List<Map<String, Object>> chunks = new ArrayList<>(chunkPlan.initialChunks());

            if (chunks.isEmpty()) {
                verdict = AiVerdict.COULD_NOT_PROCESS;
                log.info("AI verification aborted due to no usable chunks: postId={}, eventId={}", event.getPostId(), event.getEventId());
                persistResult(post, verdict, 0.0d, 0.0d, "No usable evidence chunks could be extracted from the provided sources.", context.sourcesUsed());
                meterRegistry.counter("verification_failed_total", "reason", "no_chunks").increment();
                status = "no_chunks";
                return;
            }

            boolean gotVerdict = false;
            int totalExtraFetched = 0;
            int nextCandidateIndex = 0;

            for (int turn = 1; turn <= MAX_AGENT_TURNS && !gotVerdict; turn++) {
                meterRegistry.counter("verification_model_calls_total").increment();
                List<Map<String, Object>> requestChunks = buildRequestChunks(chunks);
                log.info(
                    "AI verification turn {} started: postId={}, eventId={}, chunksCount={}, chunkChars={}, totalTrackedChunks={}",
                    turn,
                    event.getPostId(),
                    event.getEventId(),
                    requestChunks.size(),
                    estimateChunkChars(requestChunks),
                    chunks.size()
                );

                // Use structured /v1/verify endpoint if provider supports it
                String assistantContent = null;
                if (aiChatProvider instanceof HuggingFaceSpacesProvider hfProvider) {
                    assistantContent = hfProvider.verifyClaimWithChunks(
                        context.claim(),
                        requestChunks,
                        turn,
                        MAX_AGENT_TURNS,
                        event.getEventId()
                    );
                } else if (aiChatProvider instanceof FailoverAiChatProvider failover) {
                    assistantContent = failover.verifyClaimWithChunks(
                        context.claim(),
                        requestChunks,
                        turn,
                        MAX_AGENT_TURNS,
                        event.getEventId()
                    );
                } else {
                    log.debug("Provider {} does not support structured /verify endpoint, using chat fallback", aiChatProvider.getProviderName());
                    break;
                }

                if (assistantContent == null) {
                    break;
                }

                log.info(
                    "AI verification raw structured response: postId={}, eventId={}, turn={}, chars={}, preview={}",
                    event.getPostId(),
                    event.getEventId(),
                    turn,
                    assistantContent == null ? 0 : assistantContent.length(),
                    abbreviate(assistantContent, MAX_LOGGED_ASSISTANT_CHARS)
                );

                try {
                    VerificationResult vr = null;
                    if (aiChatProvider instanceof HuggingFaceSpacesProvider hfProvider) {
                        vr = hfProvider.parseStructuredResponse(assistantContent);
                    } else if (aiChatProvider instanceof FailoverAiChatProvider failover) {
                        vr = failover.parseStructuredResponse(assistantContent);
                    } else {
                        vr = aiChatProvider.parseVerificationResultFromContent(assistantContent);
                    }

                    if (vr.getVerdict() == AiVerdict.PROCESSING) {
                        // Model asked for more context; we respond with the next most relevant unused chunk.
                        if (totalExtraFetched < MAX_EXTRA_CHUNKS && nextCandidateIndex < chunkPlan.remainingChunks().size()) {
                            Map<String, Object> newChunk = chunkPlan.remainingChunks().get(nextCandidateIndex++);
                            if (newChunk != null && canAppendChunk(chunks, newChunk)) {
                                chunks.add(newChunk);
                                totalExtraFetched++;
                                log.info(
                                    "AI verification added next relevant chunk: postId={}, eventId={}, chunkId={}, documentId={}, totalFetched={}",
                                    event.getPostId(),
                                    event.getEventId(),
                                    newChunk.get("chunk_id"),
                                    newChunk.get("document_id"),
                                    totalExtraFetched
                                );
                                continue;
                            }
                        }
                    }

                    // Got a final verdict
                    if (vr.getVerdict() != AiVerdict.PROCESSING) {
                        verdict = vr.getVerdict() == null ? AiVerdict.COULD_NOT_PROCESS : vr.getVerdict();
                        persistResult(post, verdict, vr.getConfidenceScore(), vr.getEvidenceScore(), vr.getExplanation(), mergeSources(context.sourcesUsed(), vr.getSourcesUsed()));
                        usedSources = mergeSources(context.sourcesUsed(), vr.getSourcesUsed());
                        meterRegistry.counter("verification_verdict_total", "verdict", verdict.name()).increment();
                        log.info(
                            "AI verification completed: postId={}, eventId={}, verdict={}, confidenceScore={}, turn={}",
                            event.getPostId(),
                            event.getEventId(),
                            verdict,
                            vr.getConfidenceScore(),
                            turn
                        );
                        gotVerdict = true;
                    }
                } catch (Exception ex) {
                    log.warn(
                        "Structured response parsing failed on turn {}: {}", 
                        turn, 
                        ex.getMessage()
                    );
                }
            }

            if (!gotVerdict) {
                verdict = AiVerdict.COULD_NOT_PROCESS;
                persistResult(post, verdict, 0.0d, 0.0d, "Agent did not return a verdict within allowed turns/limits.", context.sourcesUsed());
                meterRegistry.counter("verification_failed_total", "reason", "no_verdict").increment();
                log.warn(
                    "AI verification ended without a verdict: postId={}, eventId={}, maxTurns={}, maxExtraChunks={}",
                    event.getPostId(),
                    event.getEventId(),
                    MAX_AGENT_TURNS,
                    MAX_EXTRA_CHUNKS
                );
            }
        } catch (Exception ex) {
            meterRegistry.counter("verification_failed_total", "reason", "exception").increment();
            status = "exception";
            log.error("Verification failed for post verification pipeline", ex);
            if (post != null) {
                try {
                    persistResult(post, AiVerdict.COULD_NOT_PROCESS, 0.0d, 0.0d, "Verification pipeline failed before a verdict could be produced.", usedSources);
                } catch (Exception persistEx) {
                    log.error("Failed to persist COULD_NOT_PROCESS fallback for postId={}", post.getId(), persistEx);
                }
            }
        } finally {
            sample.stop(Timer.builder("ai_verification_processing_duration")
                .description("Time spent verifying a post claim")
                .tag("status", status)
                .register(meterRegistry));
        }
    }

    private VerificationContext buildContext(Post post) {
        List<String> sources = sanitizeSources(post.getSources());
        if (sources.isEmpty()) {
            return new VerificationContext(extractClaim(post.getContent()), "", List.of());
        }

        int initialLimit = Math.min(MAX_INITIAL_SOURCES, sources.size());
        List<EvidenceFetcher.FetchedSource> fetchedSources = new ArrayList<>(evidenceFetcher.fetchAll(sources, initialLimit));
        List<String> usedSources = fetchedSources.stream().map(EvidenceFetcher.FetchedSource::getUrl).toList();

        List<ScoredPassage> scoredPassages = scorePassages(post.getContent(), fetchedSources);
        double bestScore = scoredPassages.isEmpty() ? 0.0d : scoredPassages.get(0).score();

        if (bestScore < WEAK_EVIDENCE_THRESHOLD && sources.size() > initialLimit) {
            EvidenceFetcher.FetchedSource extraSource = evidenceFetcher.fetch(sources.get(initialLimit));
            fetchedSources.add(extraSource);
            usedSources = fetchedSources.stream().map(EvidenceFetcher.FetchedSource::getUrl).toList();
            scoredPassages = scorePassages(post.getContent(), fetchedSources);
        }

        String claim = extractClaim(post.getContent());
        String evidenceContext = formatEvidenceContext(claim, scoredPassages);
        return new VerificationContext(claim, evidenceContext, usedSources);
    }

    /**
     * Build structured chunks array for /verify endpoint with document_id, chunk_id, text, score
     */
    private StructuredChunkPlan buildStructuredChunkPlan(Post post) {
        List<String> sources = sanitizeSources(post.getSources());
        if (sources.isEmpty()) {
            return new StructuredChunkPlan(List.of(), List.of(), Map.of());
        }

        int initialLimit = Math.min(MAX_INITIAL_SOURCES, sources.size());
        List<EvidenceFetcher.FetchedSource> fetchedSources = new ArrayList<>(evidenceFetcher.fetchAll(sources, initialLimit));

        List<ScoredPassage> scoredPassages = rankPassages(post.getContent(), fetchedSources);
        double bestScore = scoredPassages.isEmpty() ? 0.0d : scoredPassages.get(0).score();

        if (bestScore < WEAK_EVIDENCE_THRESHOLD && sources.size() > initialLimit) {
            EvidenceFetcher.FetchedSource extraSource = evidenceFetcher.fetch(sources.get(initialLimit));
            fetchedSources.add(extraSource);
            scoredPassages = rankPassages(post.getContent(), fetchedSources);
        }

        Map<String, String> documentIdToUrl = new HashMap<>();

        // Map sources to document_ids for later lookup
        for (int i = 0; i < fetchedSources.size(); i++) {
            EvidenceFetcher.FetchedSource source = fetchedSources.get(i);
            String docId = "doc-" + i;
            documentIdToUrl.put(docId, source.getUrl());
        }

        // Build initial chunks array and a separate queue of remaining ranked chunks.
        List<Map<String, Object>> chunks = new ArrayList<>();
        List<Map<String, Object>> remainingChunks = new ArrayList<>();
        int totalChars = 0;
        for (ScoredPassage passage : scoredPassages) {
            // Find which document this passage belongs to
            String documentId = "doc-0"; // default
            for (int i = 0; i < fetchedSources.size(); i++) {
                if (fetchedSources.get(i).getUrl().equals(passage.url())) {
                    documentId = "doc-" + i;
                    break;
                }
            }

            String trimmedText = truncateText(passage.text(), MAX_CHUNK_TEXT_CHARS);
            if (trimmedText.isBlank()) {
                continue;
            }
            if (totalChars + trimmedText.length() > MAX_INITIAL_TOTAL_CHUNK_CHARS) {
                break;
            }

            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("document_id", documentId);
            chunk.put("chunk_id", chunks.size() + remainingChunks.size());
            chunk.put("text", trimmedText);
            chunk.put("score", passage.score());
            if (chunks.size() < MAX_SELECTED_PASSAGES && totalChars + trimmedText.length() <= MAX_INITIAL_TOTAL_CHUNK_CHARS) {
                chunks.add(chunk);
                totalChars += trimmedText.length();
            } else {
                remainingChunks.add(chunk);
            }
        }

        return new StructuredChunkPlan(chunks, remainingChunks, documentIdToUrl);
    }

    private int estimateChunkChars(List<Map<String, Object>> chunks) {
        int total = 0;
        for (Map<String, Object> chunk : chunks) {
            Object textObj = chunk.get("text");
            if (textObj instanceof String text) {
                total += text.length();
            }
        }
        return total;
    }

    private List<Map<String, Object>> buildRequestChunks(List<Map<String, Object>> allChunks) {
        if (allChunks == null || allChunks.isEmpty()) {
            return List.of();
        }
        if (allChunks.size() <= 3) {
            return new ArrayList<>(allChunks);
        }

        // Keep two stable anchor chunks and rotate in the newest chunk.
        List<Map<String, Object>> selected = new ArrayList<>(3);
        selected.add(allChunks.get(0));
        selected.add(allChunks.get(1));
        selected.add(allChunks.get(allChunks.size() - 1));
        return selected;
    }

    private boolean canAppendChunk(List<Map<String, Object>> chunks, Map<String, Object> newChunk) {
        Object textObj = newChunk.get("text");
        int newChars = textObj instanceof String text ? text.length() : 0;
        return estimateChunkChars(chunks) + newChars <= MAX_TOTAL_CHUNK_CHARS;
    }

    private String truncateText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars).trim();
    }

    private List<ScoredPassage> rankPassages(String content, List<EvidenceFetcher.FetchedSource> sources) {
        Set<String> claimTokens = tokenize(content);
        List<ScoredPassage> passages = new ArrayList<>();

        for (EvidenceFetcher.FetchedSource source : sources) {
            if (source == null || source.getChunks() == null) {
                continue;
            }

            for (String chunk : source.getChunks()) {
                if (chunk == null || chunk.isBlank()) {
                    continue;
                }

                double score = overlapScore(claimTokens, tokenize(chunk));
                if (score <= 0.0d) {
                    continue;
                }

                passages.add(new ScoredPassage(score, source.getUrl(), source.getTitle(), chunk));
            }
        }

        return passages.stream()
            .sorted(Comparator.comparingDouble(ScoredPassage::score).reversed())
            .collect(Collectors.toList());
    }

    private List<String> sanitizeSources(List<String> sources) {
        if (sources == null) {
            return List.of();
        }

        return sources.stream()
            .filter(source -> source != null && !source.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    private boolean handleActionRequest(PostCreatedEvent event, com.fasterxml.jackson.databind.JsonNode node, List<Map<String, String>> messages) {
        if (node == null || !node.has("action")) {
            return false;
        }

        String action = node.path("action").asText("");
        int fetchedThisTurn = 0;
        StringBuilder additional = new StringBuilder();

        log.info(
            "AI verification requested more evidence: postId={}, eventId={}, turn action={}",
            event.getPostId(),
            event.getEventId(),
            action
        );

        if ("request_chunk".equals(action)) {
            String url = node.path("url").asText("");
            int idx = node.has("index") ? node.path("index").asInt(0) : 0;
            String chunk = evidenceFetcher.getChunk(url, idx);
            if (chunk != null && !chunk.isBlank()) {
                additional.append("Additional evidence from ").append(url).append(" index ").append(idx).append(":\n");
                additional.append(chunk).append("\n");
                fetchedThisTurn = 1;
                log.info(
                    "AI verification fetched chunk: postId={}, eventId={}, url={}, index={}, chars={}",
                    event.getPostId(),
                    event.getEventId(),
                    url,
                    idx,
                    chunk.length()
                );
            }
        } else if ("request_chunks".equals(action)) {
            String url = node.path("url").asText("");
            if (node.has("indices") && node.path("indices").isArray()) {
                List<Integer> indices = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode in : node.path("indices")) {
                    indices.add(in.asInt());
                }
                List<String> chunks = evidenceFetcher.getChunks(url, indices);
                for (int i = 0; i < chunks.size(); i++) {
                    String c = chunks.get(i);
                    if (c != null && !c.isBlank()) {
                        additional.append("Additional evidence from ").append(url).append(" index ").append(indices.get(i)).append(":\n");
                        additional.append(c).append("\n");
                        fetchedThisTurn++;
                        log.info(
                            "AI verification fetched chunk: postId={}, eventId={}, url={}, index={}, chars={}",
                            event.getPostId(),
                            event.getEventId(),
                            url,
                            indices.get(i),
                            c.length()
                        );
                    }
                }
            }
        }

        if (fetchedThisTurn == 0) {
            log.warn(
                "AI verification requested more evidence but nothing was fetched: postId={}, eventId={}, action={}, rawResponse={}",
                event.getPostId(),
                event.getEventId(),
                action,
                abbreviate(node.toString(), MAX_LOGGED_ASSISTANT_CHARS)
            );
            return false;
        }

        messages.add(Map.of("role", "user", "content", additional.toString()));
        log.info(
            "AI verification appended extra evidence and will continue: postId={}, eventId={}, fetchedThisTurn={}",
            event.getPostId(),
            event.getEventId(),
            fetchedThisTurn
        );
        return true;
    }

    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content.trim();
    }

    private String abbreviate(String text, int maxChars) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private String extractClaim(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String cleaned = content.trim().replaceAll("\\s+", " ");
        if (cleaned.length() <= CLAIM_MAX_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, CLAIM_MAX_LENGTH).trim();
    }

    private List<ScoredPassage> scorePassages(String content, List<EvidenceFetcher.FetchedSource> sources) {
        Set<String> claimTokens = tokenize(content);
        List<ScoredPassage> passages = new ArrayList<>();

        for (EvidenceFetcher.FetchedSource source : sources) {
            if (source == null || source.getChunks() == null) {
                continue;
            }

            for (String chunk : source.getChunks()) {
                if (chunk == null || chunk.isBlank()) {
                    continue;
                }

                double score = overlapScore(claimTokens, tokenize(chunk));
                if (score <= 0.0d) {
                    continue;
                }

                passages.add(new ScoredPassage(score, source.getUrl(), source.getTitle(), chunk));
            }
        }

        return passages.stream()
            .sorted(Comparator.comparingDouble(ScoredPassage::score).reversed())
            .limit(MAX_SELECTED_PASSAGES)
            .collect(Collectors.toList());
    }

    private double overlapScore(Set<String> claimTokens, Set<String> evidenceTokens) {
        if (claimTokens.isEmpty() || evidenceTokens.isEmpty()) {
            return 0.0d;
        }

        Set<String> overlap = new HashSet<>(claimTokens);
        overlap.retainAll(evidenceTokens);
        return (double) overlap.size() / (double) claimTokens.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }

        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .filter(token -> token.length() > 2)
            .collect(Collectors.toSet());
    }

    private String formatEvidenceContext(String claim, List<ScoredPassage> passages) {
        if (passages.isEmpty()) {
            return "Claim: " + claim + "\nNo evidence passages were strong enough to include.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Claim:\n").append(claim).append("\n\nEvidence passages:\n");
        for (int i = 0; i < passages.size(); i++) {
            ScoredPassage passage = passages.get(i);
            builder.append(i + 1)
                .append(". [")
                .append(passage.score())
                .append("] ")
                .append(passage.title() == null || passage.title().isBlank() ? passage.url() : passage.title())
                .append("\n")
                .append(passage.text())
                .append("\n\n");
        }
        return builder.toString();
    }

    private List<String> mergeSources(List<String> primarySources, List<String> modelSources) {
        Set<String> merged = new java.util.LinkedHashSet<>();
        if (primarySources != null) {
            merged.addAll(primarySources);
        }
        if (modelSources != null) {
            merged.addAll(modelSources);
        }
        return new ArrayList<>(merged);
    }

    private void persistResult(Post post, AiVerdict verdict, double confidenceScore, double evidenceScore, String explanation, List<String> sourcesUsed) {
        PostVerification verification = postVerificationRepository.findByPostId(post.getId()).orElseGet(PostVerification::new);
        verification.setPost(post);
        verification.setVerdict(verdict);
        verification.setConfidenceScore(confidenceScore);
        verification.setEvidenceScore(evidenceScore);
        verification.setExplanation(explanation);
        try {
            verification.setSourcesUsed(objectMapper.writeValueAsString(sourcesUsed == null ? List.of() : sourcesUsed));
        } catch (Exception ex) {
            verification.setSourcesUsed("[]");
        }
        verification.setProcessedAt(LocalDateTime.now());
        postVerificationRepository.save(verification);

        post.setAiVerdict(verdict);
        postRepository.save(post);
    }

    public Optional<PostVerificationDto> getVerification(Long postId) {
        return postVerificationRepository.findByPostId(postId)
            .map(this::toDto);
    }

    private PostVerificationDto toDto(PostVerification verification) {
        List<String> sources = List.of();
        if (verification.getSourcesUsed() != null && !verification.getSourcesUsed().isBlank()) {
            try {
                sources = objectMapper.readValue(verification.getSourcesUsed(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception ex) {
                sources = List.of();
            }
        }

        Long postId = verification.getPost() == null ? null : verification.getPost().getId();
        return new PostVerificationDto(
            postId,
            verification.getVerdict(),
            verification.getConfidenceScore(),
            verification.getEvidenceScore(),
            verification.getExplanation(),
            sources,
            verification.getProcessedAt()
        );
    }

    private record VerificationContext(String claim, String evidenceContext, List<String> sourcesUsed) {
    }

    private record StructuredChunkPlan(List<Map<String, Object>> initialChunks, List<Map<String, Object>> remainingChunks, Map<String, String> documentIdToUrl) {
    }

    private record ScoredPassage(double score, String url, String title, String text) {
    }
}
