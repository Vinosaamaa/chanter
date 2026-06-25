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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaasPlanSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void newStudyServerDefaultsToStarterPlanAndOwnerCanUpgrade() throws Exception {
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
                .andExpect(jsonPath("$.planTier").value("STARTER"))
                .andExpect(jsonPath("$.aiInvocationLimit").value(5));

        mockMvc.perform(patch("/api/v1/study-servers/{studyServerId}/saas-plan", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "planTier", "PRO"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planTier").value("PRO"))
                .andExpect(jsonPath("$.aiInvocationLimit").value(100));

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
