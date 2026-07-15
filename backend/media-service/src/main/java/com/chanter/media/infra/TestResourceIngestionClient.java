package com.chanter.media.infra;

import com.chanter.media.application.ResourceIngestionClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestResourceIngestionClient implements ResourceIngestionClient {

    public record IngestCall(UUID courseId, UUID resourceId, String fileName, byte[] content) {
    }

    private final List<IngestCall> ingestCalls = new ArrayList<>();
    private final List<UUID> deleteCalls = new ArrayList<>();

    @Override
    public void ingestAiApprovedResource(UUID courseId, UUID resourceId, String fileName, byte[] content) {
        ingestCalls.add(new IngestCall(courseId, resourceId, fileName, content));
    }

    @Override
    public void deleteResourceChunks(UUID resourceId) {
        deleteCalls.add(resourceId);
    }

    public List<IngestCall> ingestCalls() {
        return List.copyOf(ingestCalls);
    }

    public List<UUID> deleteCalls() {
        return List.copyOf(deleteCalls);
    }

    public void clear() {
        ingestCalls.clear();
        deleteCalls.clear();
    }
}
