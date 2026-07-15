package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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

@SpringBootTest(properties = {
        "chanter.internal-service-token=test-internal-service-token-for-agent",
        "chanter.embeddings.provider=hashing",
        "chanter.embeddings.dimensions=64"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmbeddingRetrievalSmokeTest {

    private static final String INTERNAL_TOKEN = "test-internal-service-token-for-agent";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestEmbedsAndRetrieveRespectsGrants() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID grantedResourceId = UUID.randomUUID();
        UUID otherResourceId = UUID.randomUUID();

        ingest(courseId, grantedResourceId, "homework.txt", """
                Homework help guide.
                Submit homework before the deadline using the course portal.
                Late work needs instructor approval.
                """);
        ingest(courseId, otherResourceId, "weather.txt", """
                Weather notes for outdoor labs.
                Check the forecast before field trips.
                """);

        MvcResult embedResult = mockMvc.perform(post(
                        "/api/v1/internal/resource-chunks/{resourceId}/embed",
                        grantedResourceId
                )
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode embedBody = objectMapper.readTree(embedResult.getResponse().getContentAsString());
        assertThat(embedBody.get("embeddingCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(embedBody.get("modelId").asText()).contains("hashing");

        Map<String, Object> retrieveBody = new LinkedHashMap<>();
        retrieveBody.put("query", "How do I submit homework before the deadline?");
        retrieveBody.put("grantedResourceIds", List.of(grantedResourceId));
        retrieveBody.put("topK", 3);

        MvcResult retrieveResult = mockMvc.perform(post("/api/v1/internal/resource-chunks/retrieve")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(retrieveBody)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode results = objectMapper.readTree(retrieveResult.getResponse().getContentAsString()).get("results");
        assertThat(results.isArray()).isTrue();
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).get("resourceId").asText()).isEqualTo(grantedResourceId.toString());
        assertThat(results.get(0).get("score").asDouble()).isGreaterThan(0.0);
        assertThat(results.get(0).get("contentText").asText().toLowerCase()).contains("homework");

        Map<String, Object> ungated = new LinkedHashMap<>();
        ungated.put("query", "How do I submit homework before the deadline?");
        ungated.put("grantedResourceIds", List.of(otherResourceId));
        ungated.put("topK", 3);

        MvcResult ungatedResult = mockMvc.perform(post("/api/v1/internal/resource-chunks/retrieve")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(ungated)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ungatedResults = objectMapper.readTree(ungatedResult.getResponse().getContentAsString()).get("results");
        for (JsonNode node : ungatedResults) {
            assertThat(node.get("resourceId").asText()).isNotEqualTo(grantedResourceId.toString());
        }
    }

    private void ingest(UUID courseId, UUID resourceId, String fileName, String text) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", courseId);
        body.put("resourceId", resourceId);
        body.put("fileName", fileName);
        body.put("contentBase64", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/v1/internal/resource-chunks/ingest")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated());
    }
}
