package com.chanter.common.events;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class OutboxDispatcher {
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final Map<String, URI> destinations;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public OutboxDispatcher(DurableOutbox outbox, ObjectMapper mapper, Map<String, URI> destinations, String token) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.destinations = Map.copyOf(destinations);
        this.token = InternalServiceTokens.require(token);
    }

    public void drain() {
        for (int count = 0; count < 25; count++) {
            var candidate = outbox.claim();
            if (candidate.isEmpty()) return;
            var delivery = candidate.get();
            try {
                URI destination = destinations.get(delivery.destination());
                if (destination == null) throw new IllegalStateException("Unknown destination");
                var request = HttpRequest.newBuilder(destination).timeout(Duration.ofSeconds(5))
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(delivery.event()))).build();
                int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status >= 200 && status < 300) outbox.delivered(delivery);
                else outbox.failed(delivery, "HTTP_" + status);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                outbox.failed(delivery, "INTERRUPTED");
                return;
            } catch (Exception exception) {
                outbox.failed(delivery, "DELIVERY_FAILED");
            }
        }
    }
}
