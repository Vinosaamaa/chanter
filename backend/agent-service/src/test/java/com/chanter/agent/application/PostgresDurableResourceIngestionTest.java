package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.*;
import com.chanter.common.events.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "MEDIA_INTEGRATION", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5544/chanter_media?currentSchema=agent_jobs",
        "spring.datasource.username=media_test",
        "spring.datasource.password=media-test-only-password",
        "spring.flyway.schemas=agent_jobs",
        "spring.flyway.default-schema=agent_jobs"
})
class PostgresDurableResourceIngestionTest extends DurableResourceIngestionTest {
    @Test void expiredClaimAndAcceptedEventSurviveProcessAndDatabaseRestart() throws Exception {
        UUID resource = UUID.fromString("a5a1e955-e315-4d29-8cb2-a31605d65d98");
        UUID course = UUID.fromString("1d95445a-d65d-4f52-9d91-254c5c786ed6");
        UUID server = UUID.fromString("b7717e04-8252-49d2-973e-af3d413bf225");
        UUID eventId = UUID.fromString("53fb96d7-44fb-44b9-b2ec-b1f70a4b7641");
        byte[] bytes = "Durable process restart evidence".getBytes(StandardCharsets.UTF_8);
        var change = new ResourceChanged(resource, course, server, ResourceIngestionService.sha256Bytes(bytes), "restart.txt", true, false);
        var event = new DurableEvent(eventId, 1, "media", 100, "RESOURCE_CHANGED", "RESOURCE:" + resource, mapper.writeValueAsString(change));
        var consumer = new DurableConsumer(template, new TransactionTemplate(transactions));
        if (!"true".equals(System.getenv("MEDIA_RESTART_PHASE"))) {
            assertThat(consumer.apply(event, false, () -> jobs.accept(event, change))).isTrue();
            assertThat(jobs.claim().orElseThrow().resourceId()).isEqualTo(resource);
            // Simulate a worker process ending after its durable claim, before final publication.
            assertThat(chunks.findByResourceId(resource)).isEmpty();
        } else {
            assertThat(consumer.apply(event, false, () -> jobs.accept(event, change))).isFalse();
            assertThat(jobs.status(resource, eventId).status()).isEqualTo("PROCESSING");
            jdbc.sql("UPDATE resource_index_lifecycle SET job_lease_until=:expired WHERE resource_id=:id")
                    .param("expired", java.time.OffsetDateTime.now().minusHours(1)).param("id", resource).update();
            var reclaimed = jobs.claim().orElseThrow();
            assertThat(reclaimed.resourceId()).isEqualTo(resource);
            ingestion.ingestClaim(reclaimed, bytes);
            assertThat(chunks.findByResourceId(resource)).extracting(c -> c.contentText()).containsExactly("Durable process restart evidence");
            assertThat(jobs.status(resource, eventId).status()).isEqualTo("READY");
        }
    }
}
