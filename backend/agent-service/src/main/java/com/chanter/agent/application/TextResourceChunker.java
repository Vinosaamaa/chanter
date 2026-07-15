package com.chanter.agent.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted resource text into overlapping chunks with stable character offsets
 * into the original UTF-16 Java string (compatible with {@link String#substring(int, int)}).
 */
public final class TextResourceChunker {

    public static final int DEFAULT_TARGET_CHARS = 800;
    public static final int DEFAULT_OVERLAP_CHARS = 100;

    private final int targetChars;
    private final int overlapChars;

    public TextResourceChunker() {
        this(DEFAULT_TARGET_CHARS, DEFAULT_OVERLAP_CHARS);
    }

    public TextResourceChunker(int targetChars, int overlapChars) {
        if (targetChars < 1) {
            throw new IllegalArgumentException("targetChars must be >= 1");
        }
        if (overlapChars < 0 || overlapChars >= targetChars) {
            throw new IllegalArgumentException("overlapChars must be >= 0 and < targetChars");
        }
        this.targetChars = targetChars;
        this.overlapChars = overlapChars;
    }

    public List<ChunkSpan> chunk(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        int length = text.length();
        if (length <= targetChars) {
            return List.of(new ChunkSpan(0, length, text));
        }

        List<ChunkSpan> spans = new ArrayList<>();
        int start = 0;
        while (start < length) {
            int idealEnd = Math.min(start + targetChars, length);
            int end = idealEnd;
            if (idealEnd < length) {
                end = preferBreak(text, start, idealEnd);
            }
            if (end <= start) {
                end = Math.min(start + targetChars, length);
            }
            spans.add(new ChunkSpan(start, end, text.substring(start, end)));
            if (end >= length) {
                break;
            }
            int nextStart = Math.max(end - overlapChars, start + 1);
            if (nextStart >= length) {
                break;
            }
            start = nextStart;
        }
        return List.copyOf(spans);
    }

    private static int preferBreak(String text, int start, int idealEnd) {
        int windowStart = Math.max(start + 1, idealEnd - 120);
        for (int i = idealEnd; i >= windowStart; i--) {
            char c = text.charAt(i - 1);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        for (int i = idealEnd; i >= windowStart; i--) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c)) {
                return i;
            }
        }
        return idealEnd;
    }

    public record ChunkSpan(int startOffset, int endOffset, String text) {
    }
}
