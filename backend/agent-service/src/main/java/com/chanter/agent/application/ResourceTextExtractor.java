package com.chanter.agent.application;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ResourceTextExtractor {

    private ResourceTextExtractor() {
    }

    public static boolean supportsFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    /**
     * Extracts UTF-8 text from supported extensions. Unsupported types (e.g. PDF) return empty.
     */
    public static String extract(byte[] content, String fileName) {
        if (content == null || content.length == 0 || !supportsFileName(fileName)) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}
