package com.chanter.common.telemetry;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/** Production diagnostics never serialize free-form messages, arguments, MDC or exception messages. */
public final class SafeLogFormatter implements StructuredLogFormatter<ILoggingEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String service;
    private final String release;
    private final String environment;
    private final boolean tracing;

    public SafeLogFormatter(Environment settings) {
        service = token(settings.getProperty("spring.application.name"), "unknown-service");
        String revision = settings.getProperty("CHANTER_RELEASE", "");
        release = revision.matches("[a-f0-9]{40}") ? revision : "unversioned";
        String name = settings.getProperty("CHANTER_ENVIRONMENT", "development");
        environment = Set.of("production", "staging", "test", "development").contains(name) ? name : "unknown";
        tracing = "true".equals(settings.getProperty("CHANTER_TELEMETRY_ENABLED"));
    }

    @Override public String format(ILoggingEvent event) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("time", event.getInstant().toString());
        record.put("level", event.getLevel().toString());
        record.put("service", service); record.put("release", release); record.put("environment", environment);
        record.put("logger", token(event.getLoggerName(), "unknown"));
        record.put("event", "application.log"); record.put("tracing.enabled", tracing);
        Map<String, String> context = event.getMDCPropertyMap();
        id(record, context, "trace_id", "traceId", "[a-f0-9]{32}");
        id(record, context, "span_id", "spanId", "[a-f0-9]{16}");
        id(record, context, "request_id", "requestId", "[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");
        IThrowableProxy failure = event.getThrowableProxy();
        if (failure != null) {
            record.put("event", "application.exception");
            List<Object> errors = new ArrayList<>();
            for (int depth = 0; failure != null && depth < 4; depth++, failure = failure.getCause()) {
                List<Object> frames = new ArrayList<>();
                if (failure.getStackTraceElementProxyArray() != null) {
                    for (var proxy : failure.getStackTraceElementProxyArray()) {
                        var frame = proxy.getStackTraceElement();
                        if (frame.getClassName().startsWith("com.chanter.") && frame.getLineNumber() > 0
                                && token(frame.getClassName(), "").length() > 0
                                && token(frame.getMethodName(), "").length() > 0) {
                            frames.add(Map.of("class", frame.getClassName(), "method", frame.getMethodName(), "line", frame.getLineNumber()));
                        }
                        if (frames.size() == 12) break;
                    }
                }
                errors.add(Map.of("type", token(failure.getClassName(), "unknown"), "frames", frames));
            }
            record.put("errors", errors);
        }
        try { return JSON.writeValueAsString(record) + "\n"; }
        catch (JsonProcessingException impossible) { return "{\"event\":\"logging.serialization_failed\"}\n"; }
    }
    private static String token(String value, String fallback) {
        return value != null && value.matches("[A-Za-z_$][A-Za-z0-9_.$-]{0,179}") ? value : fallback;
    }
    private static void id(Map<String, Object> record, Map<String, String> context, String name, String alternate, String pattern) {
        String value = context.getOrDefault(name, context.get(alternate));
        if (value != null && value.matches(pattern)) record.put(name, value);
    }
}
