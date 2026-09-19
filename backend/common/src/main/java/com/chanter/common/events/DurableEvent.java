package com.chanter.common.events;

import java.util.UUID;

/** Versioned delivery contract. Payload interpretation belongs to the destination. */
public record DurableEvent(UUID id, int schemaVersion, String producer, long revision,
        String kind, String aggregateKey, String payload) {
    public void validate() {
        if (id == null || schemaVersion != 1 || revision < 1 || producer == null
                || !java.util.Set.of("community", "message", "media").contains(producer)
                || kind == null || aggregateKey == null || aggregateKey.length() > 300
                || payload == null || payload.length() > 32000) {
            throw new IllegalArgumentException("Unsupported or invalid durable event");
        }
    }
}
