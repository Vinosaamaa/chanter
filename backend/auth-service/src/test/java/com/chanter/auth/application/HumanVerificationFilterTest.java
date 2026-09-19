package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.auth.api.HumanVerificationFilter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HumanVerificationFilterTest {
    @Test void routeMatrixParametersCannotBypassVerification() throws Exception {
        var filter = new HumanVerificationFilter(new TurnstileVerification("public-key", "fixture-secret", "https://study.example", true));
        for (String suffix : new String[]{";review=1", "%3Breview=1"}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/register" + suffix);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Ambiguous route reached registration"); });
            assertThat(response.getStatus()).isEqualTo(400);
        }
    }

    @Test void explicitEmailAlternativeAndSessionEndpointsDoNotDependOnChallengeProvider() throws Exception {
        var verification = new TurnstileVerification("public-key", "fixture-secret", "https://study.example", true);
        var filter = new HumanVerificationFilter(verification);
        for (String path : new String[]{"register", "forgot-password", "login", "logout", "reset-password", "verify-email"}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/" + path);
            if (path.equals("register") || path.equals("forgot-password")) request.addHeader("X-Chanter-Verification-Method", "email");
            var called = new java.util.concurrent.atomic.AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
            assertThat(called).isTrue();
        }
    }

    @Test void rejectedAndUnavailableChallengesNeverReachRegistration() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/verify", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            var verification = new TurnstileVerification("public-key", "fixture-secret", "https://study.example", true,
                    HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/verify"));
            for (boolean unavailable : new boolean[]{false, true}) {
                var request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
                if (unavailable) request.addHeader("X-Chanter-Bot-Token", "opaque-token");
                var response = new MockHttpServletResponse();
                new HumanVerificationFilter(verification).doFilter(request, response, (req, res) -> { throw new AssertionError("Reached registration"); });
                assertThat(response.getStatus()).isEqualTo(unavailable ? 503 : 428);
                assertThat(response.getContentAsString()).contains("email verification instead").doesNotContain("opaque-token");
                assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            }
        } finally { server.stop(0); }
    }
}
