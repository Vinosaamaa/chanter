package com.chanter.common.events;

import java.util.UUID;

/** Versioned delivery contract. Payload interpretation belongs to the destination. */
public record DurableEvent(UUID id, int schemaVersion, String producer, long revision,
        String kind, String aggregateKey, String payload) {
    private static final java.util.Set<String> PRODUCERS = java.util.Set.of("auth", "community", "message", "media", "agent", "notification", "search");
    public void validate() {
        if (id == null || schemaVersion != 1 || revision < 1 || producer == null
                || !PRODUCERS.contains(producer)
                || kind == null || kind.isBlank() || kind.length() > 40
                || aggregateKey == null || aggregateKey.isBlank() || aggregateKey.length() > 300
                || payload == null || payload.length() > 65536) {
            throw new IllegalArgumentException("Unsupported or invalid durable event");
        }
    }
}
