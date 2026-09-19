package com.chanter.common.telemetry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class SafeLogFormatterTest {
    @Test void diagnosticsKeepReleaseCorrelationAndApplicationFramesWithoutUserContent() throws Exception {
        var settings = new MockEnvironment().withProperty("spring.application.name", "auth-service")
                .withProperty("CHANTER_RELEASE", "a".repeat(40)).withProperty("CHANTER_ENVIRONMENT", "production");
        var event = new LoggingEvent();
        event.setInstant(Instant.now()); event.setLevel(Level.ERROR); event.setLoggerName("com.chanter.auth.Login");
        event.setMessage("password=private-canary query={}"); event.setArgumentArray(new Object[] { "private-canary" });
        event.setMDCPropertyMap(Map.of("authorization", "private-canary", "trace_id", "1".repeat(32), "span_id", "2".repeat(16)));
        var failure = new IllegalStateException("private-canary", new RuntimeException("private-canary"));
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.chanter.auth.Login", "authenticate", "Login.java", 42),
                new StackTraceElement("third.party.Document", "private-canary", "private-canary", 5) });
        event.setThrowableProxy(new ThrowableProxy(failure));
        String encoded = new SafeLogFormatter(settings).format(event);
        assertFalse(encoded.contains("private-canary"));
        var json = new ObjectMapper().readTree(encoded);
        assertEquals("a".repeat(40), json.get("release").asText());
        assertEquals("1".repeat(32), json.get("trace_id").asText());
        assertEquals(42, json.get("errors").get(0).get("frames").get(0).get("line").asInt());
        assertEquals("application.exception", json.get("event").asText());
        assertFalse(json.get("tracing.enabled").asBoolean());
    }
}
