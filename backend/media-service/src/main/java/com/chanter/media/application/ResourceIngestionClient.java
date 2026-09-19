package com.chanter.media.application;

import java.util.UUID;

public interface ResourceIngestionClient {

    Outcome ingestAiApprovedResource(UUID courseId, UUID resourceId, String fileName, byte[] content);
    Outcome status(UUID resourceId, UUID eventId, String sourceSha256);

    record Outcome(String status, java.util.Set<String> signals) {
        public Outcome {
            if (!java.util.Set.of("PENDING", "PROCESSING", "READY", "EMPTY", "OCR_REQUIRED", "ENCRYPTED", "MALFORMED", "UNSUPPORTED", "LIMIT_EXCEEDED", "FAILED", "NONE").contains(status)) {
                throw new IllegalArgumentException("Unknown extraction outcome");
            }
            signals = signals == null ? java.util.Set.of() : java.util.Set.copyOf(signals);
            if (!java.util.Set.of("DIRECTIONAL_CONTROLS", "INSTRUCTION_MARKERS", "VISUAL_CONTENT_NOT_EXTRACTED", "HEADER_FOOTER_NOT_EXTRACTED", "SPEAKER_NOTES_NOT_EXTRACTED").containsAll(signals)) {
                throw new IllegalArgumentException("Unknown extraction signal");
            }
        }
    }

    void deleteResourceChunks(UUID resourceId);

    void purgeResourceChunks(UUID resourceId);
}
