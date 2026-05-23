package com.socialfeed.service;

import com.socialfeed.dto.VerificationResult;
import java.util.List;
import java.util.Map;

/**
 * Abstraction for AI chat providers (Groq, Hugging Face Spaces, etc.)
 * Handles message-based chat interactions and verification result parsing.
 */
public interface AiChatProvider {

    /**
     * Send a message list and get a response from the AI model.
     * @param messages List of message maps with 'role' and 'content' keys
     * @return The model's response text
     */
    String chat(List<Map<String, String>> messages);

    /**
     * Parse a verification result from the raw model response content.
     * @param content The raw response from the model
     * @return Parsed VerificationResult with verdict and metadata
     */
    VerificationResult parseVerificationResultFromContent(String content);

    /**
     * Get a human-readable name for this provider (for logging).
     * @return Provider name (e.g., "Hugging Face Spaces", "Groq")
     */
    String getProviderName();
}
