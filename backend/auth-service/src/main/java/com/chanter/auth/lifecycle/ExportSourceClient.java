package com.chanter.auth.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.lifecycle.AccountExportProtocol;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

/** Fixed deployment destinations and bounded private responses. Browser input never selects a host or path. */
public final class ExportSourceClient {
    private static final int MANIFEST_BYTES = 4 * 1024 * 1024;
    private final Map<String, URI> sources;
    private final String token;
    private final ObjectMapper mapper;
    private final ExportSnapshotStore local;
    public ExportSourceClient(Map<String, URI> sources, String token, ObjectMapper mapper, ExportSnapshotStore local) {
        if (!sources.keySet().equals(AccountExportProtocol.SOURCES.stream().filter(source -> !source.equals("auth")).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("Incomplete export destinations");
        for (URI uri : sources.values()) if (uri.getHost() == null || !java.util.Set.of("http", "https").contains(uri.getScheme())
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException("Invalid export destination");
        InternalServiceTokens.requireBytes(token);
        this.sources = Map.copyOf(sources); this.token = token; this.local = local;
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
        var connection = (HttpURLConnection) base.resolve(path).toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(10_000);
        connection.setRequestProperty(AuthHeaders.INTERNAL_SERVICE_TOKEN, token);
        connection.setRequestProperty("Accept-Encoding", "identity");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("EXPORT_SOURCE_UNAVAILABLE");
            if (connection.getContentLengthLong() > limit) throw new IOException("EXPORT_SOURCE_LIMIT");
            String encoding = connection.getContentEncoding();
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw new IOException("EXPORT_SOURCE_ENCODING");
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length > limit) throw new IOException("EXPORT_SOURCE_LIMIT");
                return bytes;
            }
        } finally { connection.disconnect(); }
    }
    private static String path(UUID id) { return "/api/v1/internal/lifecycle/exports/" + id; }
}
