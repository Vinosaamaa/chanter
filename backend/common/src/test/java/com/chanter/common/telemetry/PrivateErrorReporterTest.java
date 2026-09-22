package com.chanter.common.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.LoggerContext;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PrivateErrorReporterTest {
    private static final String DSN = "https://" + "a".repeat(32) + "@o1.ingest.us.sentry.io/1";
    @Test void disabledNeedsNoCredentialsAndInvalidDestinationsAreRejected() {
        new ApplicationContextRunner().withUserConfiguration(PrivateErrorsConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(PrivateErrorReporter.class));
        for (String invalid : new String[]{"", "http://" + "a".repeat(32) + "@o1.ingest.us.sentry.io/1",
                DSN + "?private-canary", DSN.replace("sentry.io", "private.example"), DSN.replace("@", ":private-canary@")}) {
            assertThatThrownBy(() -> PrivateErrorReporter.options(invalid)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Error reporting requires a valid Sentry HTTPS DSN");
        }
    }
    @Test void realSdkAndLogAppenderExportOnlyTheRebuiltEvent() throws Exception {
        var received = new AtomicReference<String>(); var delivered = new CountDownLatch(1);
        var deliveries = new java.util.concurrent.atomic.AtomicInteger();
        var collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/api/1/envelope/", exchange -> {
            try (var body = "gzip".equals(exchange.getRequestHeaders().getFirst("Content-Encoding"))
                    ? new GZIPInputStream(exchange.getRequestBody()) : exchange.getRequestBody()) {
                byte[] bytes = body.readNBytes(32769);
                if (bytes.length > 32768) { exchange.sendResponseHeaders(413, -1); return; }
                received.set(new String(bytes, StandardCharsets.UTF_8));
                deliveries.incrementAndGet();
                exchange.sendResponseHeaders(200, -1); delivered.countDown();
            } finally { exchange.close(); }
        });
        collector.start();
        var options = PrivateErrorReporter.options(DSN);
        // Only this embedded fixture overrides HTTPS; production configuration rejects it.
        options.setDsn("http://" + "a".repeat(32) + "@127.0.0.1:" + collector.getAddress().getPort() + "/1");
        var context = new LoggerContext();
        try (var reporter = new PrivateErrorReporter(options, context, "auth-service", "b".repeat(40), "test")) {
            reporter.start();
            var failure = new IllegalStateException("private-canary");
            context.getLogger("private-canary").error("private-canary", failure);
            assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get()).doesNotContain("private-canary", "server_name", "breadcrumbs", "threads", "request", "user");
            assertThat(received.get()).contains("auth-service", "b".repeat(40), "IllegalStateException", "realSdkAndLogAppenderExportOnlyTheRebuiltEvent");
            for (int index = 0; index < 199; index++) context.getLogger("fixture").error("private-canary", failure);
            reporter.close();
            assertThat(deliveries.get()).isEqualTo(5);
        } finally { context.stop(); collector.stop(0); }
    }
    @Test void bootEnablesTheReporterOnlyWithExplicitValidConfiguration() {
        var root = ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory()).getLogger("ROOT");
        new ApplicationContextRunner().withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(PrivateErrorsConfiguration.class))
                .withPropertyValues("chanter.errors.enabled=true", "chanter.errors.dsn=" + DSN, "spring.application.name=auth-service")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PrivateErrorReporter.class);
                    assertThat(root.getAppender("chanter-private-errors")).isNotNull();
                });
        assertThat(root.getAppender("chanter-private-errors")).isNull();
    }
    @Test void receiverOutageAndQueuePressureDoNotBlockApplicationLoggingOrShutdown() throws Exception {
        int port;
        try (var unused = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) { port = unused.getLocalPort(); }
        var options = PrivateErrorReporter.options(DSN);
        options.setDsn("http://" + "a".repeat(32) + "@127.0.0.1:" + port + "/1");
        var context = new LoggerContext();
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(6), () -> {
            try (var reporter = new PrivateErrorReporter(options, context, "auth-service", "b".repeat(40), "test")) {
                reporter.start();
                for (int index = 0; index < 200; index++)
                    context.getLogger("fixture").error("private-canary", new IllegalStateException("private-canary"));
            }
        });
        assertThat(context.getLogger("ROOT").getAppender("chanter-private-errors")).isNull();
        context.stop();
    }
}
