package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "chanter.beta.assistant-run-limit=17")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaasPlanSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void ownerCannotRaiseAStudyServerQuotaThroughTheFormerPlanEndpoint() throws Exception {
        UUID ownerUserId = UUID.randomUUID();

        MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Quota Demo Server"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID studyServerId = UUID.fromString(
                objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("id").asText()
        );

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/saas-plan", studyServerId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planTier").value("FREE_BETA"))
                .andExpect(jsonPath("$.aiInvocationLimit").value(17));

        mockMvc.perform(patch("/api/v1/study-servers/{studyServerId}/saas-plan", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "planTier", "PRO"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/saas-plan", studyServerId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planTier").value("FREE_BETA"))
                .andExpect(jsonPath("$.aiInvocationLimit").value(17));

        org.junit.jupiter.api.Assertions.assertEquals("STARTER", jdbcClient
                .sql("SELECT plan_tier FROM study_servers WHERE id = :id")
                .param("id", studyServerId).query(String.class).single());

        jdbcClient.sql("UPDATE study_servers SET plan_tier = 'ORGANIZATION' WHERE id = :id")
                .param("id", studyServerId).update();
        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/saas-plan", studyServerId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planTier").value("FREE_BETA"))
                .andExpect(jsonPath("$.aiInvocationLimit").value(17))
                .andExpect(jsonPath("$.entitlementSource").value("OPERATOR_POLICY"))
                .andExpect(jsonPath("$.usageWindow").value("LIFETIME"));

        UUID strangerUserId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/study-servers/{studyServerId}/saas-plan", studyServerId)
                        .with(asUser(strangerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "planTier", "ORGANIZATION"
                        ))))
                .andExpect(status().isForbidden());
    }
}
