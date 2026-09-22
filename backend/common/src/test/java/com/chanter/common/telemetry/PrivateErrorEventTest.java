package com.chanter.common.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.sentry.JsonSerializer;
import io.sentry.SentryOptions;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrivateErrorEventTest {
    @Test void eventIsRebuiltFromBoundedCodeFramesWithoutMessagesContextOrPaths() throws Exception {
        var failure = new IllegalStateException("private-canary");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.chanter.media.UploadService", "save", "C:/private-canary/User.java", 42),
                new StackTraceElement("private-canary", "private-canary", "private-canary", 21)
        });
        var log = new LoggingEvent(); log.setLevel(Level.ERROR); log.setLoggerName("private-canary");
        log.setMessage("private-canary"); log.setArgumentArray(new Object[]{"private-canary"});
        log.setMDCPropertyMap(Map.of("user", "private-canary", "Authorization", "private-canary"));
        log.setThrowableProxy(new ThrowableProxy(failure));
        var event = PrivateErrorEvent.from(log, "media-service", "a".repeat(40), "test");
        var output = new StringWriter(); new JsonSerializer(new SentryOptions()).serialize(event, output);
        assertThat(output.toString()).doesNotContain("private-canary", "server_name", "request", "breadcrumbs", "threads", "user");
        assertThat(event.getExceptions()).hasSize(1);
        assertThat(event.getExceptions().getFirst().getStacktrace().getFrames()).hasSize(1);
        assertThat(event.getExceptions().getFirst().getStacktrace().getFrames().getFirst().getLineno()).isEqualTo(42);
        assertThat(event.getRelease()).isEqualTo("a".repeat(40));
    }
    @Test void ordinaryMessagesAndWarningsAreNotExportedAsErrors() {
        var log = new LoggingEvent(); log.setLevel(Level.ERROR); log.setMessage("private-canary");
        assertThat(PrivateErrorEvent.from(log, "auth-service", "a".repeat(40), "test")).isNull();
        var warning = new LoggingEvent(); warning.setLevel(Level.WARN);
        warning.setThrowableProxy(new ThrowableProxy(new IllegalArgumentException("private-canary")));
        assertThat(PrivateErrorEvent.from(warning, "auth-service", "a".repeat(40), "test")).isNull();
    }
    @Test void longCauseAndStackChainsRemainBounded() {
        var frames = java.util.stream.IntStream.range(0, 200).mapToObj(index ->
                new StackTraceElement("com.chanter.auth.AccountService", "read", "private-canary", index + 1))
                .toArray(StackTraceElement[]::new);
        Throwable failure = null;
        for (int index = 0; index < 8; index++) {
            var next = new IllegalStateException("private-canary", failure); next.setStackTrace(frames); failure = next;
        }
        var log = new LoggingEvent(); log.setLevel(Level.ERROR); log.setThrowableProxy(new ThrowableProxy(failure));
        var event = PrivateErrorEvent.from(log, "auth-service", "a".repeat(40), "test");
        assertThat(event.getExceptions()).hasSize(4).allSatisfy(error ->
                assertThat(error.getStacktrace().getFrames()).hasSize(12));
    }
}
