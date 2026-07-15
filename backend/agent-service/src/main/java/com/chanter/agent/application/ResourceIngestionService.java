package com.chanter.agent.application;

import com.chanter.agent.domain.ResourceChunk;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResourceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ResourceIngestionService.class);

    private final ResourceChunkRepository repository;
    private final TextResourceChunker chunker;
    private final EmbeddingPipelineService embeddingPipelineService;
    private final Clock clock;

    public ResourceIngestionService(
            ResourceChunkRepository repository,
            EmbeddingPipelineService embeddingPipelineService,
            Clock clock
    ) {
        this.repository = repository;
        this.chunker = new TextResourceChunker();
        this.embeddingPipelineService = embeddingPipelineService;
        this.clock = clock;
    }

    @Transactional
    public IngestResult ingest(
            UUID courseId,
            UUID resourceId,
            String fileName,
            byte[] content
    ) {
        if (courseId == null || resourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId and resourceId are required");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileName is required");
        }

        String text = ResourceTextExtractor.extract(content, fileName);
        String contentSha256 = sha256Hex(text == null ? "" : text);
        String safeFileName = fileName.trim();

        if (text.isEmpty()) {
            repository.replaceAllForResource(resourceId, List.of());
            embeddingPipelineService.embedResource(resourceId);
            log.info(
                    "Resource ingestion cleared chunks resourceId={} courseId={} fileName={} reason=empty_or_unsupported",
                    resourceId,
                    courseId,
                    safeFileName
            );
            return new IngestResult(resourceId, courseId, 0, contentSha256, true);
        }

        List<TextResourceChunker.ChunkSpan> spans = chunker.chunk(text);
        var createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<ResourceChunk> chunks = new ArrayList<>(spans.size());
        for (int i = 0; i < spans.size(); i++) {
            TextResourceChunker.ChunkSpan span = spans.get(i);
            chunks.add(new ResourceChunk(
                    UUID.randomUUID(),
                    resourceId,
                    courseId,
                    i,
                    span.startOffset(),
                    span.endOffset(),
                    span.text(),
                    contentSha256,
                    safeFileName,
                    createdAt
            ));
        }

        repository.replaceAllForResource(resourceId, chunks);
        embeddingPipelineService.embedResource(resourceId);
        log.info(
                "Resource ingestion stored chunks resourceId={} courseId={} fileName={} chunkCount={} contentSha256={}",
                resourceId,
                courseId,
                safeFileName,
                chunks.size(),
                contentSha256
        );
        return new IngestResult(resourceId, courseId, chunks.size(), contentSha256, false);
    }

    @Transactional
    public void deleteByResourceId(UUID resourceId) {
        if (resourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceId is required");
        }
        repository.deleteByResourceId(resourceId);
        log.info("Resource ingestion deleted chunks resourceId={}", resourceId);
    }

    @Transactional(readOnly = true)
    public List<ResourceChunk> listByResourceId(UUID resourceId) {
        return repository.findByResourceId(resourceId);
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record IngestResult(
            UUID resourceId,
            UUID courseId,
            int chunkCount,
            String contentSha256,
            boolean empty
    ) {
    }
}
