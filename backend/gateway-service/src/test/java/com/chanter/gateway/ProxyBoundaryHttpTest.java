package com.chanter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.JwtTokenService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "chanter.jwt.secret=chanter-test-jwt-secret-32bytes-min!!", "chanter.edge.public-origin=https://study.example" })
class ProxyBoundaryHttpTest {
    private static final AtomicReference<Headers> delivered = new AtomicReference<>();
    private static final HttpServer upstream = startUpstream();
    @Value("${local.server.port}") int port;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    private static HttpServer startUpstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", request -> {
                delivered.set(request.getRequestHeaders());
                request.getRequestBody().readAllBytes();
                request.sendResponseHeaders(200, 0);
                request.getResponseBody().close();
            });
            server.start();
            return server;
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    @AfterAll static void stopUpstream() { upstream.stop(0); }

    @Test void actualGatewayRemovesSpoofedAuthorityForPublicAuth() throws Exception {
        var response = HttpClient.newHttpClient().send(request("/api/v1/auth/login")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
        assertSanitized();
        assertThat(response.headers().firstValue("X-Request-Id").orElseThrow()).isEqualTo(delivered.get().getFirst("X-Request-Id"));
        assertThat(delivered.get().getFirst("X-Request-Id")).isNotEqualTo("forged-correlation");
        assertThat(delivered.get().getFirst(AuthHeaders.USER_ID)).isNull();
    }

    @Test void jwtAuthenticationSetsTheVerifiedUserAfterSanitizingHeaders() throws Exception {
        UUID user = UUID.randomUUID();
        String token = new JwtTokenService("chanter-test-jwt-secret-32bytes-min!!", 900).createAccessToken(user);
        var response = HttpClient.newHttpClient().send(request("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
        assertSanitized();
        assertThat(delivered.get().getFirst(AuthHeaders.USER_ID)).isEqualTo(user.toString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Forwarded-For", "198.51.100.1").header("Forwarded", "for=198.51.100.1;host=evil.example")
                .header("X-Forwarded-Host", "evil.example").header("CF-Connecting-IP", "198.51.100.2")
                .header("X-Request-Id", "forged-correlation")
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "forged-service").header(AuthHeaders.USER_ID, "forged-user");
    }

    @Test void matrixParametersCannotSelectADifferentPolicyThanTheBackendRoute() throws Exception {
        for (String path : new String[]{"/api/v1/auth/register;review=1", "/api/v1/auth/register%3breview=1", "/api/v1/auth/%72egister", "/%61pi/v1/auth/register"}) {
            var response = HttpClient.newHttpClient().send(request(path)
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(400);
        }
    }

    @Test void moderationUsesAuthServiceOnlyAfterAuthenticationAndNeverExposesInternalRoutes() throws Exception {
        String token = new JwtTokenService("chanter-test-jwt-secret-32bytes-min!!", 900).createAccessToken(UUID.randomUUID());
        for (String path : new String[]{"/api/v1/moderation/reports", "/api/v1/platform-admin/cases"}) {
            var anonymous = HttpClient.newHttpClient().send(request(path).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(anonymous.statusCode()).isEqualTo(401);
            var authenticated = HttpClient.newHttpClient().send(request(path).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(authenticated.statusCode()).isEqualTo(200);
        }
        var internal = HttpClient.newHttpClient().send(request("/api/v1/internal/moderation/cases")
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(internal.statusCode()).isEqualTo(404);
    }

    private void assertSanitized() {
        assertThat(delivered.get().getFirst("X-Forwarded-For")).isEqualTo("127.0.0.1");
        assertThat(delivered.get().getFirst("X-Forwarded-Host")).isEqualTo("study.example");
        assertThat(delivered.get().getFirst("X-Forwarded-Proto")).isEqualTo("https");
        assertThat(delivered.get().getFirst("Forwarded")).isNull();
        assertThat(delivered.get().getFirst("CF-Connecting-IP")).isNull();
        assertThat(delivered.get().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN)).isNull();
    }
}
