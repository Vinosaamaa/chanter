package com.chanter.auth.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.lifecycle.AccountExportProtocol;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

/** Fixed deployment destinations and bounded private responses. Browser input never selects a host or path. */
public final class ExportSourceClient implements AutoCloseable {
    private static final int MANIFEST_BYTES = 4 * 1024 * 1024;
    private final Map<String, URI> sources;
    private final String token;
    private final ObjectMapper mapper;
    private final ExportSnapshotStore local;
    private final java.net.http.HttpClient client;
    private final java.time.Duration fetchTimeout;
    public ExportSourceClient(Map<String, URI> sources, String token, ObjectMapper mapper, ExportSnapshotStore local) {
        this(sources, token, mapper, local, java.time.Duration.ofSeconds(15));
    }
    ExportSourceClient(Map<String, URI> sources, String token, ObjectMapper mapper, ExportSnapshotStore local, java.time.Duration fetchTimeout) {
        if (!sources.keySet().equals(AccountExportProtocol.SOURCES.stream().filter(source -> !source.equals("auth")).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("Incomplete export destinations");
        for (URI uri : sources.values()) if (uri.getHost() == null || !java.util.Set.of("http", "https").contains(uri.getScheme())
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException("Invalid export destination");
        InternalServiceTokens.requireBytes(token);
        this.sources = Map.copyOf(sources); this.token = token; this.local = local;
        if (fetchTimeout.toMillis() < 1 || fetchTimeout.compareTo(java.time.Duration.ofSeconds(15)) > 0)
            throw new IllegalArgumentException("Invalid export fetch deadline");
        this.fetchTimeout = fetchTimeout;
        this.client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public ExportSnapshotStore.Manifest manifest(String source, UUID jobId, UUID accountId) throws IOException {
        if (source.equals("auth")) return local.manifest(jobId, accountId);
        var manifest = mapper.readValue(get(source, path(jobId) + "?accountId=" + accountId, MANIFEST_BYTES), ExportSnapshotStore.Manifest.class);
        if (manifest == null) throw new IOException("EXPORT_MANIFEST_UNAVAILABLE");
        try { manifest.fingerprint(); }
        catch (IllegalArgumentException invalid) { throw new IOException("EXPORT_MANIFEST_INVALID", invalid); }
        return manifest;
    }
    public byte[] page(ExportSnapshotStore.Manifest manifest, int entry, int page) throws IOException {
        if (manifest.source().equals("auth")) return local.page(manifest.jobId(), manifest.accountId(), entry, page);
        return get(manifest.source(), path(manifest.jobId()) + "/entries/" + entry + "/pages/" + page + "?accountId=" + manifest.accountId(), ExportSnapshotStore.PAGE_BYTES);
    }
    private byte[] get(String source, String path, int limit) throws IOException {
        URI base = sources.get(source);
        if (base == null) throw new IOException("EXPORT_SOURCE_UNKNOWN");
        var request = java.net.http.HttpRequest.newBuilder(base.resolve(path)).timeout(fetchTimeout)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token).header("Accept-Encoding", "identity").GET().build();
        var fetch = client.sendAsync(request, info -> {
            String encoding = info.headers().firstValue("Content-Encoding").orElse("identity");
            String failure = info.statusCode() != 200 ? "EXPORT_SOURCE_UNAVAILABLE"
                    : !encoding.equalsIgnoreCase("identity") ? "EXPORT_SOURCE_ENCODING"
                    : info.headers().firstValueAsLong("Content-Length").orElse(0) > limit ? "EXPORT_SOURCE_LIMIT" : null;
            return new LimitedBody(limit, failure);
        });
        try {
            // Completion waits for the bounded subscriber to receive the entire body.
            return fetch.get(fetchTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).body();
        } catch (java.util.concurrent.TimeoutException failure) {
            fetch.cancel(true);
            throw new IOException("EXPORT_SOURCE_TIMEOUT", failure);
        } catch (InterruptedException failure) {
            fetch.cancel(true); Thread.currentThread().interrupt();
            throw new IOException("EXPORT_SOURCE_INTERRUPTED", failure);
        } catch (java.util.concurrent.ExecutionException failure) {
            if (failure.getCause() instanceof java.net.http.HttpTimeoutException) throw new IOException("EXPORT_SOURCE_TIMEOUT", failure.getCause());
            if (failure.getCause() instanceof IOException io) throw io;
            throw new IOException("EXPORT_SOURCE_UNAVAILABLE", failure.getCause());
        } finally { if (!fetch.isDone()) fetch.cancel(true); }
    }
    @Override public void close() { client.shutdownNow(); }
    private static String path(UUID id) { return "/api/v1/internal/lifecycle/exports/" + id; }

    private static final class LimitedBody implements java.net.http.HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final String rejected;
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
        private java.util.concurrent.Flow.Subscription subscription;
        LimitedBody(int limit, String rejected) { this.limit = limit; this.rejected = rejected; }
        @Override public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            if (this.subscription != null) { subscription.cancel(); return; }
            this.subscription = subscription;
            if (rejected != null) { subscription.cancel(); result.completeExceptionally(new IOException(rejected)); }
            else subscription.request(1);
        }
        @Override public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IOException("EXPORT_SOURCE_LIMIT")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
