package com.socialfeed.service;

import com.socialfeed.dto.VerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Failover AI chat provider that tries primary (Hugging Face Spaces) first,
 * then falls back to secondary (Groq) on failure.
 */
@Service
@Slf4j
@org.springframework.context.annotation.Primary
public class FailoverAiChatProvider implements AiChatProvider {

    private final AiChatProvider primary;
    private final AiChatProvider fallback;
    private final boolean fallbackEnabled;

    public FailoverAiChatProvider(
            HuggingFaceSpacesProvider huggingFaceProvider,
            GroqChatProvider groqProvider,
            @Value("${ai.fallback-enabled:true}") boolean fallbackEnabled) {
        this.primary = huggingFaceProvider;
        this.fallback = groqProvider;
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        try {
            log.info("Attempting chat with primary provider: {}", primary.getProviderName());
            String result = primary.chat(messages);
            log.info("Chat succeeded with primary provider: {}", primary.getProviderName());
            return result;
        } catch (Exception ex) {
            log.warn("Primary provider {} failed: {}", primary.getProviderName(), ex.getMessage());

            if (!fallbackEnabled) {
                log.error("Fallback is disabled, rethrowing exception from primary provider");
                throw ex;
            }

            try {
                log.info("Falling back to secondary provider: {}", fallback.getProviderName());
                String result = fallback.chat(messages);
                log.info("Chat succeeded with fallback provider: {}", fallback.getProviderName());
                return result;
            } catch (Exception fallbackEx) {
                log.error("Both primary and fallback providers failed", fallbackEx);
                throw new RuntimeException(
                    "All AI providers failed: primary=" + primary.getProviderName()
                    + " (" + ex.getMessage() + "), fallback=" + fallback.getProviderName()
                    + " (" + fallbackEx.getMessage() + ")",
                    fallbackEx);
            }
        }
    }

    @Override
    public VerificationResult parseVerificationResultFromContent(String content) {
        // Use primary provider for parsing; they're all compatible
        return primary.parseVerificationResultFromContent(content);
    }

    @Override
    public String getProviderName() {
        return "Failover[" + primary.getProviderName() + " -> " + fallback.getProviderName() + "]";
    }

    /**
     * Delegate structured verify requests to primary provider (HuggingFaceSpacesProvider)
     * with fallback handling.
     */
    public String verifyClaimWithChunks(
            String claim,
            List<Map<String, Object>> chunks,
            int turn,
            int maxTurns,
            String conversationId) {
        try {
            if (primary instanceof HuggingFaceSpacesProvider hfPrimary) {
                log.info("Attempting structured /verify with primary provider");
                String result = hfPrimary.verifyClaimWithChunks(claim, chunks, turn, maxTurns, conversationId);
                log.info("Structured verify succeeded with primary provider");
                return result;
            } else {
                log.warn("Primary provider is not HuggingFaceSpacesProvider, cannot use structured verify");
                throw new UnsupportedOperationException("Primary provider does not support structured verify endpoint");
            }
        } catch (Exception ex) {
            log.warn("Primary provider /verify failed: {}", ex.getMessage());
            if (!fallbackEnabled) {
                throw ex;
            }
            log.info("Fallback does not support /verify endpoint, failing request");
            throw new RuntimeException("Structured verify failed and fallback provider does not support it", ex);
        }
    }

    /**
     * Overload for backward compatibility
     */
    public String verifyClaimWithChunks(
            String claim,
            List<Map<String, Object>> chunks,
            int turn,
            int maxTurns) {
        return verifyClaimWithChunks(claim, chunks, turn, maxTurns, null);
    }

    /**
     * Delegate parseStructuredResponse to primary provider
     */
    public VerificationResult parseStructuredResponse(String jsonContent) {
        if (primary instanceof HuggingFaceSpacesProvider hfPrimary) {
            return hfPrimary.parseStructuredResponse(jsonContent);
        }
        throw new UnsupportedOperationException("Primary provider does not support parseStructuredResponse");
    }
}
