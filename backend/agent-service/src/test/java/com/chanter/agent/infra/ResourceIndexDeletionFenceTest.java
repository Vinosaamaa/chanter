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
    @MockitoSpyBean com.chanter.agent.application.HashingEmbeddingClient embeddingClient;
    @Autowired com.chanter.agent.application.ResourceIngestionJobs jobs;
    @Autowired org.springframework.jdbc.core.JdbcTemplate template;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test void backfillAfterDeletionIsRejectedBeforeComputingEmbeddings() {
        UUID resource = UUID.randomUUID();
        ingestion.deleteByResourceId(resource);
        assertThatThrownBy(() -> embeddings.backfillResource(resource)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void deletionCompletesWhileBackfillComputesAndRejectsItsLateWrite() throws Exception {
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
                deletion.get(2, TimeUnit.SECONDS);
                assertEmpty(resource);
            } finally { finish.countDown(); }
            assertThatThrownBy(() -> backfill.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ResponseStatusException.class);
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
        assertThat(jdbc.sql("SELECT COUNT(*) FROM resource_index_lifecycle WHERE resource_id=:id AND course_id IS NULL AND file_name IS NULL AND source_sha256 IS NULL AND parser_version IS NULL AND signals=''")
                .param("id", resource).query(Integer.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> ingestion.purgeByResourceId(resource)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ingestion.ingest(course, resource, "late.txt", "late content".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResponseStatusException.class);
        assertEmpty(resource);
    }


    @Test void deletionCompletesDuringFirstIngestionPreparationAndRejectsLateWrite() throws Exception {
        UUID resource = UUID.randomUUID();
        var computing = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        doAnswer(call -> { computing.countDown(); await(finish); return call.callRealMethod(); })
                .when(embeddingClient).embed("first slow content");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var late = executor.submit(() -> ingestion.ingest(UUID.randomUUID(), resource, "slow.txt",
                    "first slow content".getBytes(StandardCharsets.UTF_8)));
            await(computing);
            try {
                executor.submit(() -> ingestion.deleteByResourceId(resource)).get(2, TimeUnit.SECONDS);
                assertEmpty(resource);
            } finally { finish.countDown(); }
            assertThatThrownBy(() -> late.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ResponseStatusException.class);
        } finally { finish.countDown(); }
        assertEmpty(resource);
    }

    @Test void newestIngestionWinsWhenOlderEmbeddingFinishesLater() throws Exception {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID();
        var computing = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        doAnswer(call -> { computing.countDown(); await(finish); return call.callRealMethod(); })
                .when(embeddingClient).embed("older slow content");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var older = executor.submit(() -> ingestion.ingest(course, resource, "old.txt",
                    "older slow content".getBytes(StandardCharsets.UTF_8)));
            await(computing);
            try {
                executor.submit(() -> ingestion.ingest(course, resource, "new.txt",
                        "newer current content".getBytes(StandardCharsets.UTF_8))).get(2, TimeUnit.SECONDS);
            } finally { finish.countDown(); }
            assertThatThrownBy(() -> older.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ResponseStatusException.class);
        } finally { finish.countDown(); }
        assertThat(chunks.findByResourceId(resource)).extracting(ResourceChunk::contentText)
                .containsExactly("newer current content");
        assertThat(embeddingCount(resource)).isEqualTo(1);
    }

    @Test void identicalReadySourceKeepsItsChunksAndDoesNotEmbedAgain() {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID();
        byte[] content = "unchanged content".getBytes(StandardCharsets.UTF_8);
        ingestion.ingest(course, resource, "same.txt", content);
        var original = chunks.findByResourceId(resource);
        clearInvocations(embeddingClient);
        ingestion.ingest(course, resource, "same.txt", content);
        assertThat(chunks.findByResourceId(resource)).isEqualTo(original);
        verify(embeddingClient, never()).embed(anyString());
    }

    @Test void durableDeletionCompletesDuringEmbeddingAndRejectsItsLatePublication() throws Exception {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID();
        byte[] bytes = "durable delayed evidence".getBytes(StandardCharsets.UTF_8);
        queue(resource, course, server, bytes, 1);
        var claim = jobs.claim().orElseThrow();
        var computing = new CountDownLatch(1); var finish = new CountDownLatch(1);
        doAnswer(call -> { computing.countDown(); await(finish); return call.callRealMethod(); })
                .when(embeddingClient).embed("durable delayed evidence");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var late = executor.submit(() -> ingestion.ingestClaim(claim, bytes));
            await(computing);
            try { executor.submit(() -> ingestion.deleteByResourceId(resource)).get(2, TimeUnit.SECONDS); assertEmpty(resource); }
            finally { finish.countDown(); }
            assertThatThrownBy(() -> late.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ResponseStatusException.class);
        } finally { finish.countDown(); }
        assertEmpty(resource);
    }

    @Test void newerDurableEventPublishesWhileOlderEmbeddingIsStillBlocked() throws Exception {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID();
        byte[] old = "durable older evidence".getBytes(StandardCharsets.UTF_8);
        byte[] current = "durable newest evidence".getBytes(StandardCharsets.UTF_8);
        queue(resource, course, server, old, 1);
        var claim = jobs.claim().orElseThrow();
        var computing = new CountDownLatch(1); var finish = new CountDownLatch(1);
        doAnswer(call -> { computing.countDown(); await(finish); return call.callRealMethod(); })
                .when(embeddingClient).embed("durable older evidence");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var late = executor.submit(() -> ingestion.ingestClaim(claim, old));
            await(computing);
            try {
                queue(resource, course, server, current, 2);
                var replacement = jobs.claim().orElseThrow();
                executor.submit(() -> ingestion.ingestClaim(replacement, current)).get(2, TimeUnit.SECONDS);
            } finally { finish.countDown(); }
            assertThatThrownBy(() -> late.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ResponseStatusException.class);
        } finally { finish.countDown(); }
        assertThat(chunks.findByResourceId(resource)).extracting(ResourceChunk::contentText).containsExactly("durable newest evidence");
        assertThat(embeddingCount(resource)).isEqualTo(1);
    }

    private void queue(UUID resource, UUID course, UUID server, byte[] bytes, long revision) throws Exception {
        String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var change = new com.chanter.common.events.ResourceChanged(resource, course, server, sha, "guide.txt", true, false);
        var event = new com.chanter.common.events.DurableEvent(UUID.randomUUID(), 1, "media", revision,
                "RESOURCE_CHANGED", "RESOURCE:" + resource, mapper.writeValueAsString(change));
        new com.chanter.common.events.DurableConsumer(template, new TransactionTemplate(transactions))
                .apply(event, false, () -> jobs.accept(event, change));
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
