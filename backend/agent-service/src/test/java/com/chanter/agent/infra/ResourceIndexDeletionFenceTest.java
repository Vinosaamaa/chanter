package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chanter.agent.application.ResourceChunkRepository;
import com.chanter.agent.application.ResourceIngestionService;
import com.chanter.agent.application.EmbeddingPipelineService;
import com.chanter.agent.application.EmbeddingClient;
import com.chanter.agent.domain.ResourceChunk;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class ResourceIndexDeletionFenceTest {
    @Autowired ResourceChunkRepository chunks;
    @Autowired ResourceIngestionService ingestion;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EmbeddingPipelineService embeddings;
    @MockitoSpyBean EmbeddingClient embeddingClient;

    @Test void backfillAfterDeletionIsRejectedBeforeComputingEmbeddings() {
        UUID resource = UUID.randomUUID();
        ingestion.deleteByResourceId(resource);
        assertThatThrownBy(() -> embeddings.backfillResource(resource)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void deletionWaitsForDirectBackfillBeforeRemovingChunksAndEmbeddings() throws Exception {
        UUID resource = UUID.randomUUID();
        ingestion.ingest(UUID.randomUUID(), resource, "clean.txt", "backfill evidence".getBytes(StandardCharsets.UTF_8));
        var computing = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var deleting = new CountDownLatch(1);
        doAnswer(call -> { computing.countDown(); await(finish); return call.callRealMethod(); })
                .when(embeddingClient).embed("backfill evidence");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var backfill = executor.submit(() -> embeddings.backfillResource(resource));
            await(computing);
            var deletion = executor.submit(() -> { deleting.countDown(); ingestion.deleteByResourceId(resource); });
            await(deleting);
            try {
                assertThatThrownBy(() -> deletion.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { finish.countDown(); }
            backfill.get(10, TimeUnit.SECONDS);
            deletion.get(10, TimeUnit.SECONDS);
        } finally { finish.countDown(); }
        assertEmpty(resource);
    }

    @Test void deletionBeforeFirstChunkWriteRejectsPreparedLateContent() throws Exception {
        UUID resource = UUID.randomUUID();
        var prepared = List.of(chunk(resource));
        var ready = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var late = executor.submit(() -> {
                ready.countDown();
                await(finish);
                assertThatThrownBy(() -> chunks.replaceAllForResource(resource, prepared))
                        .isInstanceOf(ResponseStatusException.class);
            });
            await(ready);
            chunks.deleteByResourceId(resource);
            finish.countDown();
            late.get(10, TimeUnit.SECONDS);
        } finally { finish.countDown(); }
        assertEmpty(resource);
    }

    @Test void deletionWaitsForExistingChunkTransactionThenRemovesIt() throws Exception {
        UUID resource = UUID.randomUUID();
        var written = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var deleting = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                chunks.replaceAllForResource(resource, List.of(chunk(resource)));
                written.countDown();
                await(commit);
            }));
            await(written);
            var deletion = executor.submit(() -> {
                deleting.countDown();
                chunks.deleteByResourceId(resource);
            });
            await(deleting);
            try {
                assertThatThrownBy(() -> deletion.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { commit.countDown(); }
            writer.get(10, TimeUnit.SECONDS);
            deletion.get(10, TimeUnit.SECONDS);
        } finally { commit.countDown(); }
        assertEmpty(resource);
        assertThatThrownBy(() -> chunks.replaceAllForResource(resource, List.of(chunk(resource))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void migrationPurgeAllowsCleanReingestionButCannotUndoTerminalDeletion() {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID();
        ingestion.ingest(course, resource, "legacy.txt", "legacy content".getBytes(StandardCharsets.UTF_8));
        ingestion.purgeByResourceId(resource);
        assertEmpty(resource);
        ingestion.ingest(course, resource, "clean.txt", "clean scanned content".getBytes(StandardCharsets.UTF_8));
        assertThat(chunks.findByResourceId(resource)).extracting(ResourceChunk::contentText).containsExactly("clean scanned content");
        assertThat(embeddingCount(resource)).isPositive();
        ingestion.deleteByResourceId(resource);
        ingestion.deleteByResourceId(resource);
        assertEmpty(resource);
        assertThatThrownBy(() -> ingestion.purgeByResourceId(resource)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ingestion.ingest(course, resource, "late.txt", "late content".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResponseStatusException.class);
        assertEmpty(resource);
    }

    private void assertEmpty(UUID resource) {
        assertThat(chunks.findByResourceId(resource)).isEmpty();
        assertThat(embeddingCount(resource)).isZero();
    }
    private int embeddingCount(UUID resource) {
        return jdbc.sql("SELECT COUNT(*) FROM resource_chunk_embeddings WHERE resource_id=:id")
                .param("id", resource).query(Integer.class).single();
    }
    private static ResourceChunk chunk(UUID resource) {
        return new ResourceChunk(UUID.randomUUID(), resource, UUID.randomUUID(), 0, 0, 4, "late", "hash", "late.txt", Instant.now());
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
