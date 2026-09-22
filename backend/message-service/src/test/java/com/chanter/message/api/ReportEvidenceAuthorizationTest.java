package com.chanter.message.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.application.ChannelMessageAccessClient;
import com.chanter.message.domain.ChannelScope;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportEvidenceAuthorizationTest {
    private static final String TOKEN="test-internal-service-token-for-message";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ChannelMessageAccessClient channels;

    @Test void blockedDmRemainsReportableByParticipantsButNotStrangers() throws Exception {
        UUID sender=UUID.randomUUID(),recipient=UUID.randomUUID(),message=UUID.randomUUID();
        jdbc.update("INSERT INTO direct_messages(id,sender_user_id,recipient_user_id,body,sent_at) VALUES(?,?,?,'Preserved evidence',CURRENT_TIMESTAMP)",message,sender,recipient);
        jdbc.update("INSERT INTO user_blocks(blocker_user_id,blocked_user_id,created_at) VALUES(?,?,CURRENT_TIMESTAMP)",recipient,sender);
        mvc.perform(get("/internal/v1/moderation/evidence/DM/"+message).param("viewerId",recipient.toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.excerpt").value("Preserved evidence"));
        mvc.perform(get("/internal/v1/moderation/evidence/DM/"+message).param("viewerId",UUID.randomUUID().toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)).andExpect(status().isNotFound());
    }

    @Test void internalEvidenceDoesNotAcceptAViewerHeaderWithoutServiceAuthentication() throws Exception {
        mvc.perform(get("/internal/v1/moderation/evidence/DM/"+UUID.randomUUID())
                        .param("viewerId",UUID.randomUUID().toString()).header(AuthHeaders.USER_ID,UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test void channelEvidenceStillRequiresCurrentChannelPermission() throws Exception {
        UUID message=UUID.randomUUID(),channel=UUID.randomUUID(),viewer=UUID.randomUUID();
        jdbc.update("INSERT INTO channel_messages(id,channel_id,sender_user_id,body,created_at) VALUES(?,?,?,'Private course evidence',CURRENT_TIMESTAMP)",
                message,channel,UUID.randomUUID());
        when(channels.requireAccess(channel,viewer,ChannelScope.COURSE)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        mvc.perform(get("/internal/v1/moderation/evidence/MESSAGE/"+message).param("viewerId",viewer.toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)).andExpect(status().isForbidden());
    }
}
