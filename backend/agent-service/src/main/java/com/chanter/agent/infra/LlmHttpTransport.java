package com.chanter.agent.infra;

import com.chanter.agent.application.LlmExecution;
import com.chanter.agent.application.LlmProviderException;
import com.chanter.agent.application.LlmProviderException.Outcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class LlmHttpTransport {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private final URI baseUri;
    LlmHttpTransport(URI baseUri) { this.baseUri = baseUri; }

    JsonNode post(String path, Map<String, String> headers, Object body, LlmExecution execution) {
        return exchange(path, headers, body, execution, input -> {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new LlmProviderException(Outcome.LIMIT_EXCEEDED);
            return JSON.readTree(bytes);
        });
    }

    <T> T exchange(String path, Map<String, String> headers, Object body, LlmExecution execution, Reader<T> reader) {
        execution.check();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUri.toString().replaceAll("/$", "") + path))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
            headers.forEach(builder::header);
            CompletableFuture<HttpResponse<InputStream>> pending = HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (AutoCloseable abortRequest = execution.onCancel(() -> pending.cancel(true))) {
                HttpResponse<InputStream> response = pending.get();
                try (InputStream input = response.body(); AutoCloseable abortBody = execution.onCancel(() -> {
                    try { input.close(); } catch (java.io.IOException ignored) { }
                })) {
                    execution.check();
                    if (response.statusCode() == 429) throw new LlmProviderException(Outcome.RATE_LIMITED);
                    if (response.statusCode() < 200 || response.statusCode() >= 300) throw new LlmProviderException(Outcome.UNAVAILABLE);
                    T value = reader.read(input);
                    execution.check();
                    return value;
                }
            }
        } catch (LlmProviderException e) { throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); execution.cancel(); execution.check(); throw new LlmProviderException(Outcome.CANCELLED);
        } catch (Exception e) {
            execution.check(); throw new LlmProviderException(Outcome.UNAVAILABLE);
        }
    }
    interface Reader<T> { T read(InputStream input) throws Exception; }
    static void events(InputStream input, boolean ndjson, LlmExecution execution, Consumer<JsonNode> consumer) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder line = new StringBuilder();
        int total = 0;
        int c;
        while ((c = reader.read()) != -1) {
            if (++total > MAX_RESPONSE_BYTES || line.length() > 65_536) throw new LlmProviderException(Outcome.LIMIT_EXCEEDED);
            if (c == '\n') {
                execution.check();
                String value = line.toString().trim(); line.setLength(0);
                if (!ndjson && value.startsWith("data:")) value = value.substring(5).trim();
                else if (!ndjson) continue;
                if (value.isBlank()) continue;
                if ("[DONE]".equals(value)) { consumer.accept(null); return; }
                consumer.accept(JSON.readTree(value));
            } else if (c != '\r') line.append((char)c);
        }
        if (!line.isEmpty()) throw new LlmProviderException(Outcome.INVALID_RESPONSE);
    }
    static Integer count(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0)
            throw new LlmProviderException(Outcome.INVALID_RESPONSE);
        return value.intValue();
    }
    static String text(JsonNode node, String field) { return node.path(field).isTextual() ? node.path(field).asText() : null; }
}
