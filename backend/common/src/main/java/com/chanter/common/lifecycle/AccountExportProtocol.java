package com.chanter.common.lifecycle;

import com.chanter.common.events.DurableEvent;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;

/** IDs-only messages on the existing durable delivery protocol. Content stays in private source pages. */
public final class AccountExportProtocol {
    public static final String REQUESTED = "ACCOUNT_EXPORT_REQUESTED";
    public static final String CANCELLED = "ACCOUNT_EXPORT_CANCELLED";
    public static final String RECEIPT = "ACCOUNT_EXPORT_RECEIPT";
    public static final Set<String> SOURCES = Set.of("auth", "community", "message", "media", "agent", "notification", "search");
    private final ObjectMapper mapper;

    public AccountExportProtocol(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public ExportSnapshotStore.Request request(DurableEvent event) {
        event.validate();
        if (!"auth".equals(event.producer()) || !(REQUESTED.equals(event.kind()) || CANCELLED.equals(event.kind())))
            throw new IllegalArgumentException("Unsupported export request");
        var request = read(event.payload(), ExportSnapshotStore.Request.class);
        if (request.jobId() == null || !key(request.jobId()).equals(event.aggregateKey())) throw new IllegalArgumentException("Export scope mismatch");
        return request;
    }

    public Receipt receipt(DurableEvent event) {
        event.validate();
        var receipt = read(event.payload(), Receipt.class);
        receipt.validate();
        if (!RECEIPT.equals(event.kind()) || !receipt.source().equals(event.producer()) || !key(receipt.jobId()).equals(event.aggregateKey()))
            throw new IllegalArgumentException("Export receipt scope mismatch");
        return receipt;
    }

    public String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("Invalid export message", failure); }
    }
    public DurableEvent event(String body) {
        if (body == null || body.length() > 131_072) throw new IllegalArgumentException("Invalid export event");
        var event = read(body, DurableEvent.class);
        event.validate();
        return event;
    }
    private <T> T read(String json, Class<T> type) {
        try {
            T result = mapper.readValue(json, type);
            if (result == null) throw new IllegalArgumentException("Invalid export message");
            return result;
        }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("Invalid export message", failure); }
    }
    public static String key(UUID jobId) { return "account-export:" + jobId; }
    public record Receipt(UUID jobId, UUID accountId, String source, String state, String fingerprint) {
        public void validate() {
            if (jobId == null || accountId == null || source == null || !SOURCES.contains(source)
                    || !("READY".equals(state) && fingerprint != null && fingerprint.matches("[a-f0-9]{64}")
                    || "CANCELLED".equals(state) && fingerprint == null)) throw new IllegalArgumentException("Invalid export receipt");
        }
    }
}
