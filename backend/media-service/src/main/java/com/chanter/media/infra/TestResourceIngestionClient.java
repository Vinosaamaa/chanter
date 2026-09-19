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
    private final List<UUID> purgeCalls = new ArrayList<>();
    @Override public Outcome status(UUID resourceId, UUID eventId, String sourceSha256) {
        return new Outcome("READY", java.util.Set.of());
    }

    @Override
    public Outcome ingestAiApprovedResource(UUID courseId, UUID resourceId, String fileName, byte[] content) {
        ingestCalls.add(new IngestCall(courseId, resourceId, fileName, content));
        return new Outcome("READY", java.util.Set.of());
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

    @Override
    public void purgeResourceChunks(UUID resourceId) { purgeCalls.add(resourceId); }

    public List<UUID> purgeCalls() { return List.copyOf(purgeCalls); }

    public void clear() {
        ingestCalls.clear();
        deleteCalls.clear();
        purgeCalls.clear();
    }
}
