package com.chanter.community.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.community.application.StudyServerRepository;
import com.chanter.community.domain.OwnerRole;
import com.chanter.community.domain.SaasPlanTier;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerRole;
import com.chanter.community.domain.StudyServerType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportEvidenceAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean StudyServerRepository servers;

    @Test void currentMembershipIsRequiredEvenWhenServerIdIsKnown() throws Exception {
        UUID server=UUID.randomUUID(),member=UUID.randomUUID(),stranger=UUID.randomUUID(),owner=UUID.randomUUID();
        when(servers.findById(server)).thenReturn(Optional.of(new StudyServer(server,"Private school","Reported description",
                StudyServerType.SCHOOL,new OwnerRole(owner,StudyServerRole.STUDY_SERVER_OWNER),SaasPlanTier.FREE_BETA,List.of(),Instant.now())));
        when(servers.isStudyServerMember(server,member)).thenReturn(true);
        mvc.perform(get("/internal/v1/moderation/evidence/STUDY_SERVER/"+server).param("viewerId",member.toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-community"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(owner.toString()))
                .andExpect(jsonPath("$.title").value("Private school"));
        mvc.perform(get("/internal/v1/moderation/evidence/STUDY_SERVER/"+server).param("viewerId",stranger.toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-community"))
                .andExpect(status().isNotFound());
    }

    @Test void aViewerParameterCannotReplaceServiceAuthentication() throws Exception {
        mvc.perform(get("/internal/v1/moderation/evidence/STUDY_SERVER/"+UUID.randomUUID())
                        .param("viewerId",UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
    }
}
