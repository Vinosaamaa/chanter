package com.chanter.agent.application;

/** Only provider/store availability may degrade to a human handoff; authorization and cancellation propagate. */
public final class SemanticRetrievalUnavailableException extends RuntimeException {
    public SemanticRetrievalUnavailableException() { super("Semantic evidence is temporarily unavailable"); }
}
