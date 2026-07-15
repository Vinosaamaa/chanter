package com.chanter.media.application;

import java.util.UUID;

public interface ResourceIngestionClient {

    void ingestAiApprovedResource(UUID courseId, UUID resourceId, String fileName, byte[] content);

    void deleteResourceChunks(UUID resourceId);
}
