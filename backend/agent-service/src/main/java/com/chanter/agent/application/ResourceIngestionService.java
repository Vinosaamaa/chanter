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
    private final ResourceIndexStore indexStore;

    public ResourceIngestionService(
            ResourceChunkRepository repository,
            EmbeddingPipelineService embeddingPipelineService,
            Clock clock,
            ResourceIndexStore indexStore
    ) {
        this.repository = repository;
        this.chunker = new TextResourceChunker();
        this.embeddingPipelineService = embeddingPipelineService;
        this.clock = clock;
        this.indexStore = indexStore;
    }

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

        String safeFileName = fileName.trim();
        if (safeFileName.length() > 512) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileName is too long");
        String sourceSha256 = sha256Bytes(content == null ? new byte[0] : content);
        var attempt = indexStore.begin(courseId, resourceId, safeFileName, sourceSha256);
        if (attempt.alreadyReady()) {
            String textSha = attempt.chunks().isEmpty() ? sha256Hex("") : attempt.chunks().getFirst().contentSha256();
            return new IngestResult(resourceId, courseId, attempt.chunks().size(), textSha,
                    attempt.chunks().isEmpty(), "READY", sourceSha256, ResourceTextExtractor.PARSER_VERSION, attempt.generation());
        }
        try {
            var extraction = ResourceTextExtractor.extractDocument(content, safeFileName);
            String contentSha256 = sha256Hex(extraction.text());
            var createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            List<ResourceChunk> prepared = new ArrayList<>();
            int segmentOffset = 0;
            for (var segment : extraction.segments()) {
                var locator = segment.locator();
                for (var span : chunker.chunk(segment.text())) {
                    prepared.add(new ResourceChunk(UUID.randomUUID(), resourceId, courseId, prepared.size(),
                            segmentOffset + span.startOffset(), segmentOffset + span.endOffset(), span.text(),
                            contentSha256, safeFileName, createdAt, locator.kind(), locator.number(), locator.label(),
                            sourceSha256, ResourceTextExtractor.PARSER_VERSION));
                }
                segmentOffset += segment.text().length() + 2;
            }
            var vectors = embeddingPipelineService.prepare(prepared);
            indexStore.complete(resourceId, attempt.generation(), prepared, vectors, extraction.status().name(), extraction.signals());
            log.info("Resource extraction completed resourceId={} status={} chunkCount={}", resourceId, extraction.status(), prepared.size());
            return new IngestResult(resourceId, courseId, prepared.size(), contentSha256, prepared.isEmpty(),
                    extraction.status().name(), sourceSha256, ResourceTextExtractor.PARSER_VERSION, attempt.generation());
        } catch (RuntimeException failure) {
            indexStore.fail(resourceId, attempt.generation());
            throw failure;
        }
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

    @Transactional
    public void purgeByResourceId(UUID resourceId) {
        if (resourceId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceId is required");
        repository.purgeByResourceId(resourceId);
    }

    static String sha256Hex(String text) {
        return sha256Bytes(text.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Bytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
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
            boolean empty,
            String status,
            String sourceSha256,
            String parserVersion,
            long generation
    ) {
    }
}
