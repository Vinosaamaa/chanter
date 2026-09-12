package com.chanter.agent.application;

/** Input includes cache reads/writes. Null means the provider did not report the measurement. */
public record LlmUsage(Integer inputTokens, Integer outputTokens, Integer cacheReadTokens,
                       Integer cacheWriteTokens, Integer reasoningTokens) {
    public static final LlmUsage UNKNOWN = new LlmUsage(null, null, null, null, null);
    public LlmUsage {
        for (Integer count : new Integer[]{inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, reasoningTokens}) {
            if (count != null && count < 0) throw new IllegalArgumentException("Invalid provider usage");
        }
        if (inputTokens != null && ((cacheReadTokens != null && cacheReadTokens > inputTokens)
                || (cacheWriteTokens != null && cacheWriteTokens > inputTokens))) {
            throw new IllegalArgumentException("Invalid provider cache usage");
        }
        if (inputTokens != null && cacheReadTokens != null && cacheWriteTokens != null
                && (long) cacheReadTokens + cacheWriteTokens > inputTokens)
            throw new IllegalArgumentException("Invalid provider cache usage");
        if (outputTokens != null && reasoningTokens != null && reasoningTokens > outputTokens)
            throw new IllegalArgumentException("Invalid provider reasoning usage");
    }
    public boolean measured() { return inputTokens != null && outputTokens != null; }
}
