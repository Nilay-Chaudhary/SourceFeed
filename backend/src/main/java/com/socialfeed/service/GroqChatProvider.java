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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq AI provider implementation.
 * No tool calling to preserve token budget (6K tokens/min limit).
 */
@Service
@Slf4j
public class GroqChatProvider implements AiChatProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;
    private final String apiKey;

    public GroqChatProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.model:qwen/qwen3-32b}") String model,
            @Value("${groq.max-tokens:200}") int maxTokens,
            @Value("${groq.timeout-seconds:30}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.maxTokens = maxTokens;
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(Math.max(1, timeoutSeconds)).toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = restClientBuilder
            .baseUrl("https://api.groq.com/openai/v1")
            .requestFactory(requestFactory)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Groq API key is not configured");
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
                throw new IllegalStateException("Empty Groq response");
            }

            return response.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception ex) {
            log.error("Groq chat request failed", ex);
            throw new RuntimeException("Groq chat failed: " + ex.getMessage(), ex);
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
            log.warn("Failed to parse Groq response as JSON: {}", content, ex);
            return new VerificationResult(AiVerdict.COULD_NOT_PROCESS, 0.0d, 0.0d, "Failed to parse model response", List.of());
        }
    }

    @Override
    public String getProviderName() {
        return "Groq";
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
