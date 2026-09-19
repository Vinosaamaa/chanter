package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.TerminalJournal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.datasource.url=jdbc:h2:mem:terminal-journal-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class TerminalJournalHttpTest {
    private static final String TOKEN = "test-internal-service-token-for-auth";
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired TerminalJournalStore journal;
    @Autowired PlatformTransactionManager transactions;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test void onlyPrivateRecoveryCallerCanReadOrAcknowledgeTheExactCommittedJournal() throws Exception {
        var entry = new TransactionTemplate(transactions).execute(status -> journal.append("ACCOUNT", UUID.randomUUID()));
        assertThat(get("?after=0", null).statusCode()).isEqualTo(401);
        assertThat(get("?after=0", "incorrect-private-token").statusCode()).isEqualTo(401);
        var response = get("?after=0&limit=1", TOKEN);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        var page = mapper.readValue(response.body(), TerminalJournal.Page.class); page.validate();
        assertThat(page.entries()).containsExactly(entry);
        assertThat(get("?after=0&limit=501", TOKEN).statusCode()).isEqualTo(400);
        var checkpoint = new TerminalJournal.Checkpoint(page.through().revision(), page.through().digest(), UUID.randomUUID());
        String body = mapper.writeValueAsString(checkpoint);
        assertThat(post(body, null).statusCode()).isEqualTo(401);
        assertThat(post("null", TOKEN).statusCode()).isEqualTo(400);
        assertThat(post(body + body, TOKEN).statusCode()).isEqualTo(400);
        assertThat(post(body.substring(0, body.length() - 1) + ",\"unrecognized\":true}", TOKEN).statusCode()).isEqualTo(400);
        assertThat(post(body.substring(0, body.length() - 1) + ",\"revision\":1}", TOKEN).statusCode()).isEqualTo(400);
        assertThat(journal.replicated(entry.revision())).isFalse();
        assertThat(post(body, TOKEN).statusCode()).isEqualTo(200);
        assertThat(post(body, TOKEN).statusCode()).isEqualTo(200);
        assertThat(journal.replicated(entry.revision())).isTrue();
        assertThat(get("/checkpoint", TOKEN).body()).contains(checkpoint.checkpointId().toString());
    }
    private HttpResponse<String> get(String suffix, String token) throws Exception {
        var request = request(suffix, token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String body, String token) throws Exception {
        return client.send(request("/checkpoint", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpRequest.Builder request(String suffix, String token) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/internal/lifecycle/journal" + suffix)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token);
        return request;
    }
}
