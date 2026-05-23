package com.socialfeed.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class EvidenceFetcher {

    private static final int REQUEST_TIMEOUT_MILLIS = (int) Duration.ofSeconds(15).toMillis();
    private static final int MAX_CHUNK_CHARS = 900;
    private static final int MAX_CHUNKS_PER_SOURCE = 4;
    private static final int MIN_PARAGRAPH_CHARS = 80;

    public FetchedSource fetch(String url) {
        try {
            Document document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; SocialFeedBot/1.0)")
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .followRedirects(true)
                .get();

            String title = document.title();
            String text = cleanText(document);
            List<String> chunks = chunk(text);

            return new FetchedSource(url, title, text, chunks);
        } catch (IOException ex) {
            log.warn("Failed to fetch evidence source: url={}", url, ex);
            return new FetchedSource(url, null, null, List.of());
        }
    }

    public List<FetchedSource> fetchAll(List<String> urls, int limit) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }

        List<FetchedSource> results = new ArrayList<>();
        int safeLimit = Math.max(1, limit);
        for (int i = 0; i < urls.size() && i < safeLimit; i++) {
            results.add(fetch(urls.get(i)));
        }
        return results;
    }

    public String getChunk(String url, int index) {
        FetchedSource fetched = fetch(url);
        if (fetched == null || fetched.getChunks() == null) {
            return "";
        }
        if (index < 0 || index >= fetched.getChunks().size()) {
            return "";
        }
        return fetched.getChunks().get(index);
    }

    public List<String> getChunks(String url, List<Integer> indices) {
        List<String> out = new ArrayList<>();
        if (indices == null || indices.isEmpty()) return out;
        FetchedSource fetched = fetch(url);
        if (fetched == null || fetched.getChunks() == null) return out;
        for (Integer idx : indices) {
            if (idx == null) continue;
            int i = idx;
            if (i >= 0 && i < fetched.getChunks().size()) {
                out.add(fetched.getChunks().get(i));
            }
        }
        return out;
    }

    private String cleanText(Document document) {
        Elements removable = document.select("script, style, noscript, svg, iframe, header, footer, nav, form, aside");
        removable.remove();

        // Prefer the main article/content region over full-body extraction.
        Elements candidates = document.select("main article p, article p, #mw-content-text p, .mw-parser-output p, [role=main] p, main p");
        if (candidates.isEmpty()) {
            candidates = document.select("p");
        }

        List<String> paragraphs = new ArrayList<>();
        for (Element p : candidates) {
            String text = normalize(p.text());
            if (isUsefulParagraph(text)) {
                paragraphs.add(text);
            }
        }

        if (!paragraphs.isEmpty()) {
            return String.join(" ", paragraphs).trim();
        }

        String fallback = document.body() == null ? "" : normalize(document.body().text());
        return stripBoilerplatePhrases(fallback);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private boolean isUsefulParagraph(String text) {
        if (text == null || text.isBlank() || text.length() < MIN_PARAGRAPH_CHARS) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("create account")
            || lower.contains("log in")
            || lower.contains("sign up")
            || lower.contains("donate")
            || lower.contains("privacy policy")
            || lower.contains("terms of use")
            || lower.contains("cookie")
            || lower.contains("skip to content")
            || lower.contains("main menu")) {
            return false;
        }

        return true;
    }

    private String stripBoilerplatePhrases(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = text;
        cleaned = cleaned.replaceAll("(?i)create account", " ");
        cleaned = cleaned.replaceAll("(?i)log in", " ");
        cleaned = cleaned.replaceAll("(?i)sign up", " ");
        cleaned = cleaned.replaceAll("(?i)donate", " ");
        cleaned = cleaned.replaceAll("(?i)privacy policy", " ");
        cleaned = cleaned.replaceAll("(?i)terms of use", " ");
        cleaned = cleaned.replaceAll("(?i)cookie policy", " ");
        return normalize(cleaned);
    }

    private List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.trim();
        List<String> chunks = new ArrayList<>();
        int index = 0;
        while (index < normalized.length() && chunks.size() < MAX_CHUNKS_PER_SOURCE) {
            int end = Math.min(normalized.length(), index + MAX_CHUNK_CHARS);
            chunks.add(normalized.substring(index, end).trim());
            index = end;
        }
        return chunks;
    }

    @Data
    @AllArgsConstructor
    public static class FetchedSource {
        private String url;
        private String title;
        private String text;
        private List<String> chunks;
    }
}
