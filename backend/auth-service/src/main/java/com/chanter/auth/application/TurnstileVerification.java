package com.chanter.auth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional challenge provider. Email verification remains an explicit, rate-limited alternative. */
@Component
public final class TurnstileVerification {
    public enum Result { PASSED, REJECTED, UNAVAILABLE }
    public record Options(boolean enabled, String siteKey) {}
    private final String siteKey;
    private final String secret;
    private final String hostname;
    private final HttpClient client;
    private final URI endpoint;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public TurnstileVerification(@Value("${chanter.turnstile.site-key:}") String siteKey,
            @Value("${chanter.turnstile.secret:}") String secret,
            @Value("${chanter.public-base-url:http://localhost:5173}") String origin,
            @Value("${chanter.auth.require-email-verification:false}") boolean emailVerification) {
        this(siteKey, secret, origin, emailVerification, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"));
    }

    TurnstileVerification(String siteKey, String secret, String origin, boolean emailVerification, HttpClient client, URI endpoint) {
        this.siteKey = siteKey;
        this.secret = secret;
        this.client = client;
        this.endpoint = endpoint;
        URI publicOrigin = URI.create(origin);
        this.hostname = publicOrigin.getHost();
        if (siteKey.isBlank() != secret.isBlank()) throw new IllegalArgumentException("Turnstile requires both site and secret keys");
        if (!siteKey.isBlank() && (!siteKey.matches("[A-Za-z0-9_-]{3,100}") || !emailVerification
                || !"https".equals(publicOrigin.getScheme()) || hostname == null || publicOrigin.getUserInfo() != null
                || publicOrigin.getQuery() != null || publicOrigin.getFragment() != null
                || !(publicOrigin.getPath().isEmpty() || "/".equals(publicOrigin.getPath())))) {
            throw new IllegalArgumentException("Turnstile requires a valid site key, HTTPS public origin and enforced email verification");
        }
    }

    public Options options() { return new Options(!siteKey.isBlank(), siteKey.isBlank() ? null : siteKey); }

    public Result verify(String token, String action) {
        if (siteKey.isBlank()) return Result.PASSED;
        if (token == null || token.isBlank() || token.length() > 2048) return Result.REJECTED;
        String form = "secret=" + encode(secret) + "&response=" + encode(token) + "&idempotency_key=" + UUID.randomUUID();
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200 || response.body().length() > 16_384) return Result.UNAVAILABLE;
            var result = json.readTree(response.body());
            return result.path("success").asBoolean(false) && hostname.equalsIgnoreCase(result.path("hostname").asText())
                    && action.equals(result.path("action").asText()) ? Result.PASSED : Result.REJECTED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.UNAVAILABLE;
        } catch (IOException | RuntimeException unavailable) { return Result.UNAVAILABLE; }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
