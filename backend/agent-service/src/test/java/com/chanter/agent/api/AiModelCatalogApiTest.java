package com.chanter.agent.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.when;
import com.chanter.agent.application.StudyAssistantService;
import com.chanter.agent.application.SupportQuestionChannelAccessClient;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiModelCatalogApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean StudyAssistantService assistant;
    @MockitoBean SupportQuestionChannelAccessClient access;

    @Test void catalogRequiresAuthenticationAndReturnsOnlyFreeDefaultWhenUnconfigured() throws Exception {
        UUID user = UUID.randomUUID(), channel = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID(), install = UUID.randomUUID();
        mvc.perform(get("/api/v1/course-channels/{id}/assistant-models", channel)).andExpect(status().isUnauthorized());
        when(access.requireAccess(channel, user)).thenReturn(new SupportQuestionChannelAccessClient.SupportQuestionChannelAccess(channel, course, server, "questions", true, false));
        when(assistant.findPresence(server, user)).thenReturn(new StudyAssistantService.Presence(server, true,
                List.of(new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_CHANNEL, channel))));
        mvc.perform(get("/api/v1/course-channels/{id}/assistant-models", channel).with(AuthenticatedTestSupport.asUser(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("defaultModelId").value("source-only"))
                .andExpect(jsonPath("models.length()").value(1)).andExpect(jsonPath("models[0].billing").value("no-provider-charge"));
    }
}
