package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "chanter.internal-service-token=test-internal-service-token-for-agent")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceIngestionSmokeTest {

    private static final String INTERNAL_TOKEN = "test-internal-service-token-for-agent";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestPersistsChunksAndReIngestReplacesIdempotently() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        String firstBody = "Homework help guide.\nSubmit before the deadline.\nUse the portal.";
        String secondBody = "Updated homework help.\nNew deadline rules.\n".repeat(40);

        IngestResponse first = ingest(courseId, resourceId, "homework.txt", firstBody);
        assertThat(first.chunkCount()).isGreaterThanOrEqualTo(1);
        assertThat(first.empty()).isFalse();

        JsonNode listed = listChunks(resourceId);
        assertThat(listed.get("chunks")).hasSize(first.chunkCount());
        assertThat(listed.get("chunks").get(0).get("courseId").asText()).isEqualTo(courseId.toString());
        assertThat(listed.get("chunks").get(0).get("startOffset").asInt()).isZero();

        IngestResponse second = ingest(courseId, resourceId, "homework.txt", secondBody);
        assertThat(second.chunkCount()).isGreaterThan(first.chunkCount());
        assertThat(second.contentSha256()).isNotEqualTo(first.contentSha256());

        JsonNode replaced = listChunks(resourceId);
        assertThat(replaced.get("chunks")).hasSize(second.chunkCount());
        for (JsonNode chunk : replaced.get("chunks")) {
            assertThat(chunk.get("contentSha256").asText()).isEqualTo(second.contentSha256());
            assertThat(chunk.get("resourceId").asText()).isEqualTo(resourceId.toString());
        }

        mockMvc.perform(delete("/api/v1/internal/resource-chunks/{resourceId}", resourceId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isNoContent());

        JsonNode afterDelete = listChunks(resourceId);
        assertThat(afterDelete.get("chunks")).isEmpty();
    }

    @Test
    void unsupportedFileClearsChunksAndUnauthorizedIsRejected() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        IngestResponse pdf = ingest(courseId, resourceId, "slides.pdf", "%PDF-fake");
        assertThat(pdf.empty()).isTrue();
        assertThat(pdf.chunkCount()).isZero();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", courseId);
        body.put("resourceId", resourceId);
        body.put("fileName", "notes.txt");
        body.put("contentBase64", Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/v1/internal/resource-chunks/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isUnauthorized());
    }

    private IngestResponse ingest(UUID courseId, UUID resourceId, String fileName, String text) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", courseId);
        body.put("resourceId", resourceId);
        body.put("fileName", fileName);
        body.put("contentBase64", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));

        MvcResult result = mockMvc.perform(post("/api/v1/internal/resource-chunks/ingest")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), IngestResponse.class);
    }

    private JsonNode listChunks(UUID resourceId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/internal/resource-chunks/{resourceId}", resourceId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record IngestResponse(
            UUID resourceId,
            UUID courseId,
            int chunkCount,
            String contentSha256,
            boolean empty
    ) {
    }
}
