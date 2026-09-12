package com.chanter.agent.application;

/** Safe error codes only; never include provider response bodies, endpoints, or credentials. */
public final class LlmProviderException extends RuntimeException {
    public enum Outcome { UNAVAILABLE, RATE_LIMITED, TIMED_OUT, CANCELLED, INVALID_RESPONSE, REFUSED, LIMIT_EXCEEDED }
    private final Outcome outcome;
    public LlmProviderException(Outcome outcome) { super("AI request " + outcome.name().toLowerCase(java.util.Locale.ROOT)); this.outcome = outcome; }
    public Outcome outcome() { return outcome; }
}
