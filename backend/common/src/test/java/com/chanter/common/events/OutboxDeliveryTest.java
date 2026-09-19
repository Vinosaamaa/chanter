package com.chanter.common.events;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxDeliveryTest {
    @Test void stoppedHttpConsumerRecoversAndLostAcknowledgementDoesNotDuplicateEffects() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(DurableOutbox.SCHEMA);
        jdbc.execute(DurableConsumer.SCHEMA);
        jdbc.execute("CREATE TABLE received (id INT PRIMARY KEY)");
        var mapper = new ObjectMapper();
        var consumer = new DurableConsumer(jdbc, tx);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.start();
        server.stop(0);
        var destinations = Map.of("search", URI.create("http://127.0.0.1:" + port + "/events"));
        String token = "test-internal-event-delivery-token-32bytes";
        Instant now = Instant.now();
        var outbox = new DurableOutbox(jdbc, tx, "message", Clock.fixed(now, ZoneOffset.UTC));
        tx.executeWithoutResult(status -> outbox.append("search", "MESSAGE", "message:1", "{}"));
        new OutboxDispatcher(outbox, mapper, destinations, token).drain();
        assertThat(outbox.stats().pending()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM received", Integer.class)).isZero();
        var calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/events", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst(com.chanter.common.auth.AuthHeaders.INTERNAL_SERVICE_TOKEN)).isEqualTo(token);
            var event = mapper.readValue(exchange.getRequestBody(), DurableEvent.class);
            consumer.apply(event, false, () -> jdbc.update("INSERT INTO received VALUES (1)"));
            // Commit happened, but the sender sees a failure and must safely repeat delivery.
            exchange.sendResponseHeaders(calls.incrementAndGet() == 1 ? 503 : 204, -1);
            exchange.close();
        });
        server.start();
        try {
            var restarted = new DurableOutbox(jdbc, tx, "message", Clock.fixed(now.plusSeconds(301), ZoneOffset.UTC));
            new OutboxDispatcher(restarted, mapper, destinations, token).drain();
            assertThat(restarted.stats().pending()).isEqualTo(1);
            var retry = new DurableOutbox(jdbc, tx, "message", Clock.fixed(now.plusSeconds(602), ZoneOffset.UTC));
            new OutboxDispatcher(retry, mapper, destinations, token).drain();
            assertThat(retry.stats().delivered()).isEqualTo(1);
            assertThat(calls).hasValue(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM received", Integer.class)).isEqualTo(1);
        } finally { server.stop(0); }
    }
}
