package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.ModerationAccess;
import com.chanter.common.auth.ModerationAccess.Target;
import com.chanter.community.application.*;
import com.chanter.community.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyServerRestrictionTest {
    @Autowired StudyServerService servers;
    @Autowired CourseService courses;
    @Autowired StudyServerNavigationService navigation;
    @Autowired MockMvc mvc;
    @MockitoBean ModerationAccess moderation;

    @Test void directoryFindsAnUppercaseServerReference() throws Exception {
        var server=servers.createStudyServer("Reference search school","",StudyServerType.SCHOOL,List.of(),UUID.randomUUID());
        mvc.perform(get("/internal/v1/moderation/directory").param("query",server.id().toString().toUpperCase(Locale.ROOT))
                .header("X-Chanter-Internal-Service-Token","test-internal-service-token-for-community"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").value(server.id().toString()));
    }

    @Test void currentRestrictionBlocksEveryScopeAliasAndRestorationWorks() throws Exception {
        UUID user=UUID.randomUUID();
        var server=servers.createStudyServer("Restricted school","",StudyServerType.SCHOOL,List.of(),user);
        var course=courses.createCourseWithCohort(server.id(),user,"Course",user,"Cohort");
        var channel=course.channels().stream().filter(c -> c.kind()==ChannelKind.TEXT).findFirst().orElseThrow();
        var voice=course.channels().stream().filter(c -> c.kind()==ChannelKind.VOICE).findFirst().orElseThrow();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(moderation)
                .requireAllowed(user,List.of(new Target("STUDY_SERVER",server.id())));
        for(String path:List.of("/study-servers/"+server.id(),"/study-servers/"+server.id()+"/navigation",
                "/study-servers/"+server.id()+"/announcements","/courses/"+course.id()+"/resource-access",
                "/course-channels/"+channel.id()+"/support-question-access","/cohorts/"+course.cohort().id()+"/ta-queue-access")) {
            mvc.perform(get("/api/v1"+path).with(asUser(user))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/course-channels/"+voice.id()+"/media-token").with(asUser(user))).andExpect(status().isForbidden());
        doNothing().when(moderation).requireAllowed(user,List.of(new Target("STUDY_SERVER",server.id())));
        mvc.perform(get("/api/v1/courses/"+course.id()+"/resource-access").with(asUser(user))).andExpect(status().isOk());
    }

    @Test void restrictedServersAreAbsentFromNavigationAndAggregateSources() {
        UUID user=UUID.randomUUID();
        var server=servers.createStudyServer("Hidden school","",StudyServerType.SCHOOL,List.of(),user);
        when(moderation.allowedSources(eq(user),anyList())).thenReturn(Set.of());
        assertThat(navigation.listAccessibleStudyServers(user)).isEmpty();
        verify(moderation).allowedSources(user,List.of(new Target("STUDY_SERVER",server.id())));
    }
}
