package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class HttpCourseResourceCatalogClientTest {
    @Test void instructorCatalogAdmitsOnlyAvailableApprovedResourcesFromTheRequestedCourse() throws Exception {
        UUID course = UUID.randomUUID(), instructor = UUID.randomUUID(), available = UUID.randomUUID();
        var rows = new ArrayList<Map<String, Object>>();
        rows.add(resource(available, course, "AVAILABLE", true));
        for (String state : List.of("PROCESSING", "REJECTED", "FAILED", "DELETED", "UNKNOWN")) {
            rows.add(resource(UUID.randomUUID(), course, state, true));
        }
        rows.add(resource(UUID.randomUUID(), course, "AVAILABLE", false));
        rows.add(resource(UUID.randomUUID(), UUID.randomUUID(), "AVAILABLE", true));
        rows.add(Map.of("id", UUID.randomUUID(), "courseId", course, "aiApproved", true));
        for (String ingestion : List.of("PENDING", "PROCESSING", "FAILED", "OCR_REQUIRED", "ENCRYPTED", "UNSUPPORTED", "EMPTY", "MALFORMED", "LIMIT_EXCEEDED", "NONE", "UNKNOWN")) {
            var row = new java.util.HashMap<>(resource(UUID.randomUUID(), course, "AVAILABLE", true));
            row.put("ingestionStatus", ingestion);
            rows.add(row);
        }
        var missingIngestion = new java.util.HashMap<>(resource(UUID.randomUUID(), course, "AVAILABLE", true));
        missingIngestion.remove("ingestionStatus"); rows.add(missingIngestion);
        var seenViewer = new AtomicReference<String>();
        var seenToken = new AtomicReference<String>();
        byte[] body = new ObjectMapper().writeValueAsBytes(Map.of("courseResources", rows));
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/courses/" + course + "/course-resources", exchange -> {
            seenViewer.set(exchange.getRequestHeaders().getFirst(AuthHeaders.USER_ID));
            seenToken.set(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) { response.write(body); }
        });
        server.start();
        try {
            var client = new HttpCourseResourceCatalogClient("http://127.0.0.1:" + server.getAddress().getPort(), 1, 1, "test-internal-service-token-for-agent");
            assertThat(client.listAiApprovedCourseResources(course, instructor)).extracting(r -> r.id()).containsExactly(available);
            assertThat(seenViewer.get()).isEqualTo(instructor.toString());
            assertThat(seenToken.get()).isEqualTo("test-internal-service-token-for-agent");
        } finally { server.stop(0); }
    }

    @Test void permissionDenialAndMediaFailureNeverSupplyCachedApproval() throws Exception {
        UUID course = UUID.randomUUID(), viewer = UUID.randomUUID();
        var responseStatus = new java.util.concurrent.atomic.AtomicInteger(403);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (var response = exchange.getResponseBody()) { response.write(body); }
        });
        server.start();
        try {
            var client = new HttpCourseResourceCatalogClient("http://127.0.0.1:" + server.getAddress().getPort(), 1, 1, "test-internal-service-token-for-agent");
            assertThat(client.listAiApprovedCourseResources(course, viewer)).isEmpty();
            responseStatus.set(503);
            assertThatThrownBy(() -> client.listAiApprovedCourseResources(course, viewer)).isInstanceOf(ResponseStatusException.class);
            server.stop(0);
            assertThatThrownBy(() -> client.listAiApprovedCourseResources(course, viewer)).isInstanceOf(ResponseStatusException.class);
        } finally { server.stop(0); }
    }

    private static Map<String, Object> resource(UUID id, UUID course, String status, boolean approved) {
        return Map.of("id", id, "courseId", course, "title", "Guide", "fileName", "guide.md", "status", status, "aiApproved", approved, "ingestionStatus", "READY");
    }
}
