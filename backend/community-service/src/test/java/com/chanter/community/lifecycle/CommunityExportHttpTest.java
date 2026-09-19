package com.chanter.community.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.lifecycle.AccountExportProtocol;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="spring.datasource.url=jdbc:h2:mem:community-export-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class CommunityExportHttpTest {
    private static final String TOKEN = "test-internal-service-token-for-community";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountExportProtocol protocol;
    @Autowired ExportSnapshotStore snapshots;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test void privateHttpCaptureAndPagesRequireServiceAuthenticationAccountScopeAndUncancelledState() throws Exception {
        UUID account = UUID.randomUUID(); UUID server = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        jdbc.update("INSERT INTO study_servers(id,name,owner_user_id,created_at) VALUES (?,?,?,?)", server, "Private export fixture", account, Timestamp.from(now));
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), account, now, now.plusSeconds(86_400));
        var event = event(request, AccountExportProtocol.REQUESTED, 1);
        String body = protocol.encode(event);
        assertThat(post(body, null).statusCode()).isEqualTo(401);
        assertThat(post(body, TOKEN).statusCode()).isEqualTo(204);
        assertThat(post(body, TOKEN).statusCode()).isEqualTo(204);
        String path = "/api/v1/internal/lifecycle/exports/" + request.jobId();
        assertThat(get(path + "?accountId=" + account, null).statusCode()).isEqualTo(401);
        assertThat(get(path + "?accountId=" + UUID.randomUUID(), TOKEN).statusCode()).isEqualTo(404);
        var response = get(path + "?accountId=" + account, TOKEN);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        var manifest = mapper.readValue(response.body(), ExportSnapshotStore.Manifest.class);
        assertThat(manifest.fingerprint()).hasSize(64);
        String page = path + "/entries/0/pages/0?accountId=" + account;
        assertThat(get(page, TOKEN).body()).contains("Private export fixture");
        assertThat(get(path + "/entries/0/pages/0?accountId=" + UUID.randomUUID(), TOKEN).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=?", Integer.class, AccountExportProtocol.RECEIPT)).isEqualTo(1);
        assertThat(post(body.substring(0, body.length() - 1) + ",\"unexpected\":true}", TOKEN).statusCode()).isEqualTo(400);
        assertThat(post("null", TOKEN).statusCode()).isEqualTo(400);
        assertThat(post(protocol.encode(event(request, AccountExportProtocol.CANCELLED, 2)), TOKEN).statusCode()).isEqualTo(204);
        assertThat(get(page, TOKEN).statusCode()).isEqualTo(410);
        assertThat(post(body, TOKEN).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages WHERE snapshot_id=?", Integer.class, request.jobId())).isZero();
    }

    private DurableEvent event(ExportSnapshotStore.Request request, String kind, long revision) {
        return new DurableEvent(UUID.randomUUID(), 1, "auth", revision, kind, AccountExportProtocol.key(request.jobId()), protocol.encode(request));
    }
    @Test void protectedSnapshotPagesHaveNoPermissiveFallbackWhenSourceAccessCheckerIsMissing() throws Exception {
        UUID account = UUID.randomUUID(); UUID resource = UUID.randomUUID();
        Instant now = Instant.now();
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), account, now, now.plusSeconds(3600));
        snapshots.capture(request, output -> output.file(resource, new java.io.ByteArrayInputStream(new byte[]{1, 2, 3})));
        String path = "/api/v1/internal/lifecycle/exports/" + request.jobId();
        assertThat(get(path + "?accountId=" + account, TOKEN).statusCode()).isEqualTo(503);
        assertThat(get(path + "/entries/0/pages/0?accountId=" + account, TOKEN).statusCode()).isEqualTo(503);
    }
    private HttpResponse<String> post(String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/internal/lifecycle/events"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if (token != null) request.header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
