package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.auth.application.AuthSessionService;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.lifecycle.AccountExportProtocol;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="spring.datasource.url=jdbc:h2:mem:account-export-download-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AccountExportDownloadHttpTest {
    private static final String TOKEN = "test-internal-service-token-for-auth";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Map<String, Source> SOURCES = new HashMap<>();
    static {
        try {
            for (String source : AccountExportJobs.SOURCES) if (!source.equals("auth")) SOURCES.put(source, new Source(source));
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        SOURCES.forEach((name, fixture) -> registry.add(name.toUpperCase(java.util.Locale.ROOT) + "_SERVICE_URL",
                () -> "http://127.0.0.1:" + fixture.server.getAddress().getPort()));
    }
    @AfterAll static void stopSources() { SOURCES.values().forEach(source -> source.server.stop(0)); }
    @LocalServerPort int port;
    @Autowired AuthSessionService sessions;
    @Autowired AccountExportJobs jobs;
    @Autowired AccountExportProtocol protocol;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test void publicDownloadCollectsPrivateSourcePagesAndDeliversOnlyTheOwnersCompleteArchive() throws Exception {
        var owner = account(); var job = ready(owner);
        var result = download(job.id(), owner);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(result.headers().firstValue("Content-Disposition").orElseThrow()).contains(job.id().toString()).doesNotContain(owner.user().email());
        var entries = new HashMap<String, String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(result.body()), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(completed(result.body())).isTrue();
        assertThat(entries.get("auth/profile.jsonl")).contains(owner.user().email());
        assertThat(entries.get("community/own_data.jsonl")).contains("Private owned community content");
        assertThat(entries.keySet()).contains("manifest.json", "message/own_data.jsonl", "media/own_data.jsonl", "agent/own_data.jsonl", "search/own_data.jsonl", "notification/own_data.jsonl");
        assertThat(download(job.id(), account()).statusCode()).isEqualTo(404);
        jobs.cancel(bearer(owner), job.id());
        assertThat(download(job.id(), owner).statusCode()).isEqualTo(409);
    }
    @Test void unavailableManifestIsAnHonestFailureBeforeArchiveHeadersAndCorruptPageNeverCompletes() throws Exception {
        var owner = account(); var job = ready(owner); var media = SOURCES.get("media");
        media.unavailable.set(true);
        try {
            var result = download(job.id(), owner);
            assertThat(result.statusCode()).isEqualTo(503);
            assertThat(completed(result.body())).isFalse();
        } finally { media.unavailable.set(false); }
        media.corrupt.set(true);
        try {
            try {
                var result = download(job.id(), owner);
                assertThat(completed(result.body())).isFalse();
            } catch (java.io.IOException abortedStream) {
                // A broken HTTP transfer is also truthful; no completed archive reaches the caller.
                assertThat(abortedStream).isNotNull();
            }
        } finally { media.corrupt.set(false); }
    }
    private AccountExportJobs.Job ready(AuthSessionService.AuthSession owner) {
        var job = jobs.create(bearer(owner), UUID.randomUUID());
        var request = new ExportSnapshotStore.Request(job.id(), job.accountId(), job.requestedAt(), job.expiresAt());
        SOURCES.forEach((name, source) -> {
            var manifest = source.store.capture(request, output -> output.jsonLines("own_data", rows -> rows.add(Map.of("text", "Private owned " + name + " content"))));
            var receipt = new AccountExportProtocol.Receipt(job.id(), job.accountId(), name, "READY", manifest.fingerprint());
            jobs.accept(new DurableEvent(UUID.randomUUID(), 1, name, 1, AccountExportProtocol.RECEIPT, AccountExportProtocol.key(job.id()), protocol.encode(receipt)));
        });
        return jobs.get(bearer(owner), job.id());
    }
    private AuthSessionService.AuthSession account() {
        return sessions.registerWithStatus(UUID.randomUUID() + "@example.invalid", "private download fixture 251", "Export owner").session();
    }
    private static String bearer(AuthSessionService.AuthSession session) { return "Bearer " + session.accessToken(); }
    private HttpResponse<byte[]> download(UUID job, AuthSessionService.AuthSession owner) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/account/exports/" + job + "/download"))
                .timeout(Duration.ofSeconds(15)).header("Authorization", bearer(owner)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static boolean completed(byte[] bytes) {
        int offset = bytes.length - 22;
        return offset >= 0 && bytes[offset] == 'P' && bytes[offset + 1] == 'K' && bytes[offset + 2] == 5 && bytes[offset + 3] == 6;
    }
    private static final class Source {
        final HttpServer server;
        final ExportSnapshotStore store;
        final AtomicBoolean unavailable = new AtomicBoolean();
        final AtomicBoolean corrupt = new AtomicBoolean();
        Source(String name) throws Exception {
            var data = new DriverManagerDataSource("jdbc:h2:mem:download-source-" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(data); jdbc.execute(ExportSnapshotStore.SCHEMA);
            store = new ExportSnapshotStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(data)), JSON, Clock.systemUTC(), name);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/internal/lifecycle/exports/", exchange -> {
                try (exchange) {
                    if (!TOKEN.equals(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN))) { exchange.sendResponseHeaders(401, -1); return; }
                    if (unavailable.get()) { exchange.sendResponseHeaders(503, -1); return; }
                    String suffix = exchange.getRequestURI().getPath().substring("/api/v1/internal/lifecycle/exports/".length());
                    String[] parts = suffix.split("/"); UUID id = UUID.fromString(parts[0]);
                    UUID account = UUID.fromString(exchange.getRequestURI().getQuery().substring("accountId=".length()));
                    byte[] bytes;
                    if (parts.length == 1) bytes = JSON.writeValueAsBytes(store.manifest(id, account));
                    else {
                        bytes = store.page(id, account, Integer.parseInt(parts[2]), Integer.parseInt(parts[4]));
                        if (corrupt.get()) bytes[0] ^= 1;
                    }
                    exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }
    }
}
