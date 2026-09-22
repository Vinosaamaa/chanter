package com.chanter.common.telemetry;

import ch.qos.logback.classic.LoggerContext;
import java.net.URI;
import org.slf4j.LoggerFactory;

/** Test-only loopback transport; production still requires the validated HTTPS receiver. */
public final class ErrorReportingFixture {
    public static void main(String[] args) {
        var endpoint = URI.create(System.getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"));
        var options = PrivateErrorReporter.options("https://" + "a".repeat(32) + "@o1.ingest.us.sentry.io/1");
        options.setDsn("http://" + "a".repeat(32) + "@127.0.0.1:" + endpoint.getPort() + "/1");
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        try (var reporter = new PrivateErrorReporter(options, context, "agent-service", "a".repeat(40), "test")) {
            reporter.start();
            org.slf4j.MDC.put("user", "private-canary");
            context.getLogger("fixture").error("private-canary", new IllegalStateException("private-canary"));
            org.slf4j.MDC.clear();
        }
    }
}
