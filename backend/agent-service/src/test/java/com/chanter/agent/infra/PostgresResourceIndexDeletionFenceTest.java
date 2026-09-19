package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named = "MEDIA_INTEGRATION", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5544/chanter_media?currentSchema=agent_fence",
        "spring.datasource.username=media_test",
        "spring.datasource.password=media-test-only-password",
        "spring.flyway.schemas=agent_fence",
        "spring.flyway.default-schema=agent_fence"
})
class PostgresResourceIndexDeletionFenceTest extends ResourceIndexDeletionFenceTest {
    @Test void deletionMarkerSurvivesDatabaseRestart() {
        UUID resource = UUID.fromString("ce261092-88c0-4084-ae84-93a4b9884427");
        if (!"true".equals(System.getenv("MEDIA_RESTART_PHASE"))) {
            ingestion.deleteByResourceId(resource);
        } else {
            assertThat(jdbc.sql("SELECT deleted FROM resource_index_lifecycle WHERE resource_id=:id")
                    .param("id", resource).query(Boolean.class).single()).isTrue();
        }
        assertThatThrownBy(() -> ingestion.ingest(UUID.randomUUID(), resource, "late.txt",
                "late content".getBytes(StandardCharsets.UTF_8))).isInstanceOf(ResponseStatusException.class);
        assertThat(chunks.findByResourceId(resource)).isEmpty();
    }
}
