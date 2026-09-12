package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.chanter.agent.config.LlmProperties.Model;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "chanter.llm.daily-token-limit=100")
@ActiveProfiles("test")
class AiGenerationLedgerTest {
    @Autowired AiGenerationLedger ledger;
    @Autowired JdbcClient jdbc;
    private UUID server;
    private final Model model = new Model("Fixture", "ollama", "fixture", null, null, 64, 16, Duration.ofSeconds(3), Set.of(), null);

    @BeforeEach void install() {
        server = UUID.randomUUID();
        jdbc.sql("INSERT INTO study_assistant_installs (id,study_server_id,installed_by_user_id,installed_at) VALUES (:id,:server,:user,:at)")
                .param("id", UUID.randomUUID()).param("server", server).param("user", UUID.randomUUID())
                .param("at", OffsetDateTime.now()).update();
    }

    @Test void reservesBeforeWorkAndSettlesMeasuredUsageExactlyOnce() {
        UUID ticket = ledger.reserve(server, UUID.randomUUID(), UUID.randomUUID(), "local", model);
        assertThatThrownBy(() -> ledger.reserve(server, UUID.randomUUID(), UUID.randomUUID(), "local", model))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("429");
        ledger.settle(ticket, new LlmUsage(8, 2, null, null, null), "SUCCESS", 12, "fixture", null, model);
        ledger.settle(ticket, new LlmUsage(90, 90, null, null, null), "SUCCESS", 12, "fixture", null, model);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(10);
        assertThat(ledger.reserve(server, UUID.randomUUID(), UUID.randomUUID(), "local", model)).isNotNull();
    }

    @Test void missingProviderUsageRetainsTheReservationInsteadOfBecomingZero() {
        UUID ticket = ledger.reserve(server, UUID.randomUUID(), UUID.randomUUID(), "local", model);
        ledger.settle(ticket, LlmUsage.UNKNOWN, "TIMED_OUT", 30, "fixture", null, model);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(80);
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        assertThatThrownBy(() -> ledger.reserve(server, UUID.randomUUID(), UUID.randomUUID(), "local", model))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void concurrentReservationsCannotBothSpendTheRemainingBudget() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> reserveAfter(start));
            var second = pool.submit(() -> reserveAfter(start));
            start.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
            assertThat(ledger.summary(server).accountedTokens()).isEqualTo(80);
        }
    }
    private boolean reserveAfter(CountDownLatch start) throws Exception {
        start.await();
        try { ledger.reserve(server, UUID.randomUUID(), UUID.randomUUID(), "local", model); return true; }
        catch (ResponseStatusException e) { if (e.getStatusCode().value() != 429) throw e; return false; }
    }
}
