package com.chanter.media.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ResourceQueueMetricsTest {
    @Test void distinguishesPendingWorkFailedWorkAndCompletedResources() {
        String nativeUrl = System.getProperty("chanter.telemetry.jdbc");
        var source = nativeUrl == null
                ? new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
                : new DriverManagerDataSource(nativeUrl, "metric_test", "metric_test");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_course_resource_tables.sql"),
                new ClassPathResource("db/migration/V2__private_resource_lifecycle.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        var now = Instant.now();
        insert(jdbc, "SCANNING", "NONE", true, now.minusSeconds(120));
        insert(jdbc, "AVAILABLE", "PROCESSING", true, now.minusSeconds(90));
        insert(jdbc, "AVAILABLE", "COMPLETE", true, now.minusSeconds(100000));
        insert(jdbc, "SCAN_FAILED", "NONE", true, now.minusSeconds(60));
        insert(jdbc, "AVAILABLE", "FAILED", true, now.minusSeconds(30));
        insert(jdbc, "REJECTED", "NONE", true, now.minusSeconds(20));
        insert(jdbc, "DELETED", "PENDING", false, now.minusSeconds(100000));
        insert(jdbc, "LEGACY", "NONE", true, now.minusSeconds(150));
        var registry = new SimpleMeterRegistry();
        try (var monitor = new ResourceQueueMetrics().resourceQueueMetrics(source, registry, false)) {
            monitor.sample();
            assertThat(registry.get("chanter.resources.pending").gauge().value()).isEqualTo(4);
            assertThat(registry.get("chanter.resources.failed").gauge().value()).isEqualTo(2);
            // This is age since the oldest pending row's last update, not its original upload.
            assertThat(registry.get("chanter.resources.oldest.age").gauge().value()).isBetween(120.0, 140.0);
            var legacyRegistry = new SimpleMeterRegistry();
            try (var migration = new ResourceQueueMetrics().resourceQueueMetrics(source, legacyRegistry, true)) {
                migration.sample();
                assertThat(legacyRegistry.get("chanter.resources.pending").gauge().value()).isEqualTo(5);
                assertThat(legacyRegistry.get("chanter.resources.oldest.age").gauge().value()).isBetween(150.0, 170.0);
            } finally { legacyRegistry.close(); }
            jdbc.update("UPDATE course_resources SET state='DELETED', byte_reservation=FALSE, ingestion_status='NONE'");
            monitor.sample();
            assertThat(registry.get("chanter.resources.pending").gauge().value()).isZero();
            assertThat(registry.get("chanter.resources.failed").gauge().value()).isZero();
            assertThat(registry.get("chanter.resources.collection.healthy").gauge().value()).isEqualTo(1);
        } finally { registry.close(); }
    }
    private static void insert(JdbcTemplate jdbc, String state, String ingestion, boolean reserved, Instant updated) {
        jdbc.update("""
                INSERT INTO course_resources(id,course_id,title,file_name,content_type,byte_size,storage_key,ai_approved,
                uploaded_by_user_id,created_at,state,ingestion_status,byte_reservation,updated_at)
                VALUES(?,?,?,?,?,1,?,TRUE,?,?,?,?,?,?)
                """, UUID.randomUUID(), UUID.randomUUID(), "private-canary", "private-canary", "text/plain", "private-canary",
                UUID.randomUUID(), Timestamp.from(updated.minusSeconds(100000)), state, ingestion, reserved, Timestamp.from(updated));
    }
}
