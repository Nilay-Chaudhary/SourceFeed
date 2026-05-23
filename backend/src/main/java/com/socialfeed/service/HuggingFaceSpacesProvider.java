package com.socialfeed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialfeed.dto.VerificationResult;
import com.socialfeed.model.AiVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class HuggingFaceSpacesProvider implements AiChatProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;
    private final int maxRetries;
    private final String apiKey;
    private final String spacesUrl;

    public HuggingFaceSpacesProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${huggingface.spaces-url:}") String spacesUrl,
            @Value("${huggingface.api-key:}") String apiKey,
            @Value("${huggingface.model:meta-llama/Llama-2-7b-chat}") String model,
            @Value("${huggingface.max-tokens:200}") int maxTokens,
            @Value("${huggingface.timeout-seconds:120}") int timeoutSeconds,
            @Value("${huggingface.retries:2}") int maxRetries) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.maxTokens = maxTokens;
        this.maxRetries = Math.max(0, maxRetries);
        this.apiKey = apiKey;
        this.spacesUrl = spacesUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(Math.max(1, timeoutSeconds)).toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        String baseUrl = spacesUrl == null || spacesUrl.isBlank() ? "http://localhost:7860" : spacesUrl;
        RestClient.Builder builder = restClientBuilder
            .baseUrl(baseUrl + "/v1")
            .requestFactory(requestFactory)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        this.restClient = builder.build();
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        if (spacesUrl == null || spacesUrl.isBlank()) {
            throw new IllegalStateException("Hugging Face Spaces URL is not configured");
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("top_p", 1.0);
        requestBody.put("stream", false);

        try {
            JsonNode response = restClient.post()
                .uri("/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

            if (response == null || response.path("choices").isEmpty()) {
                throw new IllegalStateException("Empty response from Hugging Face Spaces");
            }

            return response.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception ex) {
            log.error("Hugging Face Spaces chat request failed", ex);
            throw new RuntimeException("Hugging Face Spaces chat failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public VerificationResult parseVerificationResultFromContent(String content) {
        String json = extractJson(content);
        try {
            JsonNode node = objectMapper.readTree(json);
            AiVerdict verdict = mapVerdict(node.path("verdict").asText("unclear"));
            double confidenceScore = node.path("confidenceScore").asDouble(0.0d);
            double evidenceScore = node.path("evidenceScore").asDouble(0.0d);
            String explanation = node.path("explanation").asText("");
            List<String> sourcesUsed = new ArrayList<>();
            JsonNode sourcesNode = node.path("sourcesUsed");
            if (sourcesNode.isArray()) {
                for (JsonNode source : sourcesNode) {
                    sourcesUsed.add(source.asText());
                }
            }
            return new VerificationResult(verdict, confidenceScore, evidenceScore, explanation, sourcesUsed);
        } catch (Exception ex) {
            log.warn("Failed to parse Hugging Face Spaces response as JSON: {}", content, ex);
            return new VerificationResult(AiVerdict.COULD_NOT_PROCESS, 0.0d, 0.0d, "Failed to parse model response", List.of());
        }
    }

    @Override
    public String getProviderName() {
        return "Hugging Face Spaces";
    }

    /**
     * Verify a claim with structured context: pre-fetched chunks and turn tracking.
     * Sends to the /verify endpoint (no sources—backend owns document_id→URL mapping).
     * System prompt is handled server-side.
     *
     * @param claim the statement to verify
     * @param chunks list of chunks with {document_id, chunk_id, text, score}
     * @param turn current turn number (1-indexed)
     * @param maxTurns maximum turns allowed
     * @param conversationId optional conversation identifier for session tracking
     * @return raw JSON response from model
     */
    public String verifyClaimWithChunks(
            String claim,
            List<Map<String, Object>> chunks,
            int turn,
            int maxTurns,
            String conversationId) {
        if (spacesUrl == null || spacesUrl.isBlank()) {
            throw new IllegalStateException("Hugging Face Spaces URL is not configured");
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("claim", claim);
        requestBody.put("chunks", chunks);
        requestBody.put("turn", turn);
        requestBody.put("max_turns", maxTurns);
        if (conversationId != null && !conversationId.isBlank()) {
            requestBody.put("conversation_id", conversationId);
        }

        int attempt = 0;
        long backoffSeconds = 1;
        while (true) {
            attempt++;
            try {
                String body = restClient.post()
                    .uri("/verify")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

                if (body == null || body.isBlank()) {
                    log.warn("Empty body from HF Spaces /verify: attempt={}", attempt);
                    throw new IllegalStateException("Empty body from HF Spaces /verify");
                }

                // Heuristic: check if body looks like JSON; if not, log a preview
                String trimmed = body.trim();
                boolean looksJson = trimmed.startsWith("{") || trimmed.startsWith("[");
                if (!looksJson) {
                    String preview = body.length() > 1000 ? body.substring(0, 1000) + "..." : body;
                    log.warn("HF /verify returned non-JSON response (attempt={}): preview={}", attempt, preview);
                }

                // If the Spaces returns JSON text or wraps a 'result' field, try to parse it; otherwise return raw
                String content = trimmed;
                try {
                    JsonNode node = objectMapper.readTree(content);
                    if (node.isTextual()) {
                        content = node.asText();
                    } else if (node.has("result")) {
                        content = node.path("result").asText(content);
                    }
                } catch (Exception parseEx) {
                    log.debug("Response from HF Spaces is not JSON parseable: attempt={}, parseError={}", attempt, parseEx.getMessage());
                    // keep raw content
                }

                log.info("Verify request succeeded: turn={}/{}, chunksCount={}, attempt={}", turn, maxTurns, chunks.size(), attempt);
                return content;
            } catch (Exception ex) {
                log.warn("Hugging Face Spaces /verify attempt {} failed: {}", attempt, ex.getMessage());
                if (attempt > maxRetries) {
                    log.error("Hugging Face Spaces /verify failed after {} attempts", attempt, ex);
                    throw new RuntimeException("Hugging Face Spaces /verify failed: " + ex.getMessage(), ex);
                }
                try {
                    TimeUnit.SECONDS.sleep(backoffSeconds);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while backing off retries", ie);
                }
                backoffSeconds = Math.min(30, backoffSeconds * 2);
            }
        }
    }

    /**
     * Overload for backward compatibility with optional conversationId
     */
    public String verifyClaimWithChunks(
            String claim,
            List<Map<String, Object>> chunks,
            int turn,
            int maxTurns) {
        return verifyClaimWithChunks(claim, chunks, turn, maxTurns, null);
    }

    /**
     * Parse structured verdict response: action=final_verdict or action=request_more_context
     * Expected response shapes:
     * {"action": "final_verdict", "verdict": "supported", "confidence": 0.88, "reason": "..."}
     * {"action": "request_more_context", "document_id": "paper-1", "chunk_id": 13, "direction": "next", "reason": "..."}
     */
    public VerificationResult parseStructuredResponse(String jsonContent) {
        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            String action = root.path("action").asText("");

            if ("final_verdict".equals(action)) {
                String verdict = root.path("verdict").asText("unknown");
                double confidence = root.path("confidence").asDouble(0.0);
                String reason = root.path("reason").asText("");
                
                log.debug("Parsed final verdict: verdict={}, confidence={}", verdict, confidence);
                AiVerdict mappedVerdict = mapVerdict(verdict);
                return new VerificationResult(mappedVerdict, confidence, 0.0, reason, List.of());
            } else if ("request_more_context".equals(action)) {
                String documentId = root.path("document_id").asText("");
                int chunkId = root.path("chunk_id").asInt(-1);
                String direction = root.path("direction").asText("next"); // next or prev
                String reason = root.path("reason").asText("");
                
                log.debug("Parsed context request: document_id={}, chunk_id={}, direction={}", documentId, chunkId, direction);
                // Return PROCESSING—VerificationAgent will handle fetching new chunks and looping back
                return new VerificationResult(AiVerdict.PROCESSING, 0.0, 0.0, reason, List.of());
            } else {
                log.warn("Unknown action in response: {}", action);
                return new VerificationResult(AiVerdict.COULD_NOT_PROCESS, 0.0, 0.0, "Unknown action: " + action, List.of());
            }
        } catch (Exception ex) {
            log.error("Failed to parse structured response: {}", jsonContent, ex);
            return new VerificationResult(AiVerdict.COULD_NOT_PROCESS, 0.0, 0.0, "Parse error: " + ex.getMessage(), List.of());
        }
    }

    private AiVerdict mapVerdict(String verdict) {
        return switch (verdict == null ? "" : verdict.toLowerCase()) {
            case "supported" -> AiVerdict.STRONGLY_SUPPORTED;
            case "weakly_supported" -> AiVerdict.WEAKLY_SUPPORTED;
            case "contradicted" -> AiVerdict.CONTRADICTORY;
            default -> AiVerdict.COULD_NOT_PROCESS;
        };
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
}
