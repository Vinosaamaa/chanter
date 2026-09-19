package com.chanter.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Live, bounded access checks. No positive cache may keep a suspended account active. */
public class ModerationAccess {
    private final URI endpoint;
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Semaphore inFlight = new Semaphore(32);

    public ModerationAccess(URI baseUrl, String token, ObjectMapper mapper) {
        this.endpoint = baseUrl.resolve("/internal/v1/moderation/access");
        this.token = token;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public void requireAccount(UUID user) { requireAllowed(user, List.of()); }

    public void requireAllowed(UUID user, List<Target> targets) {
        readStatus(endpoint, user, targets);
    }

    /** Filter an already source-authorized page without denying unrelated visible items in that page. */
    public Set<Target> allowedSources(UUID user, List<Target> targets) {
        JsonNode reply = readStatus(endpoint.resolve("/internal/v1/moderation/sources"), user, targets);
        if (!reply.path("restricted").isArray() || reply.path("restricted").size() > targets.size()) throw unavailable();
        Set<Target> allowed = new HashSet<>(targets);
        try {
            for (JsonNode item : reply.path("restricted")) {
                Target restricted = mapper.treeToValue(item, Target.class);
                if (!targets.contains(restricted)) throw unavailable();
                allowed.remove(restricted);
            }
        } catch (IOException invalid) { throw unavailable(); }
        return Set.copyOf(allowed);
    }

    private JsonNode readStatus(URI uri, UUID user, List<Target> targets) {
        if (targets == null || targets.size() > 100 || (user == null && targets.isEmpty()))
            throw new IllegalArgumentException("A bounded account or content target is required");
        if (!inFlight.tryAcquire()) throw unavailable();
        try {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(new AccessRequest(user, targets)))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403 || response.statusCode() == 401)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access is restricted by moderation");
            if (response.statusCode() != 200) throw unavailable();
            var json = mapper.readTree(response.body());
            if (json == null || !json.path("allowed").isBoolean() || !json.path("allowed").booleanValue()) throw unavailable();
            return json;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException failure) {
            throw unavailable();
        } finally {
            inFlight.release();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Current access status is unavailable");
    }

    public record Target(String type, UUID id) { }
    private record AccessRequest(UUID userId, List<Target> targets) { }
}
