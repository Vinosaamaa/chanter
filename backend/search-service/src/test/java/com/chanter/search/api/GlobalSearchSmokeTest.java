package com.chanter.search.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.search.application.CommunityNavigationClient;
import com.chanter.search.application.MediaCatalogClient;
import com.chanter.search.application.MessageFaqClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalSearchSmokeTest {

    private static final UUID STUDY_SERVER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COURSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LEARNER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_LEARNER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID RESOURCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID FAQ_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommunityNavigationClient communityNavigationClient;

    @MockBean
    private MediaCatalogClient mediaCatalogClient;

    @MockBean
    private MessageFaqClient messageFaqClient;

    @BeforeEach
    void setUp() {
        when(communityNavigationClient.fetchNavigation(eq(STUDY_SERVER_ID), eq(LEARNER_ID)))
                .thenReturn(new CommunityNavigationClient.StudyServerNavigation(
                        STUDY_SERVER_ID,
                        "Bootcamp Hub",
                        List.of(new CommunityNavigationClient.CourseSummary(COURSE_ID, "Spring Boot"))
                ));

        when(communityNavigationClient.fetchNavigation(eq(STUDY_SERVER_ID), eq(OTHER_LEARNER_ID)))
                .thenReturn(new CommunityNavigationClient.StudyServerNavigation(
                        STUDY_SERVER_ID,
                        "Bootcamp Hub",
                        List.of()
                ));

        when(mediaCatalogClient.listCourseResources(COURSE_ID, LEARNER_ID))
                .thenReturn(List.of(new MediaCatalogClient.CourseResourceSummary(
                        RESOURCE_ID,
                        COURSE_ID,
                        "Lecture Slides",
                        "slides.pdf"
                )));

        when(messageFaqClient.listApprovedFaqs(COURSE_ID, LEARNER_ID))
                .thenReturn(List.of(new MessageFaqClient.ApprovedFaqSummary(
                        FAQ_ID,
                        COURSE_ID,
                        "How do I submit homework?",
                        "Upload your homework in the resources channel."
                )));
    }

    @Test
    void enrolledLearnerCanSearchIndexedResourceAndFaq() throws Exception {
        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/search/reindex", STUDY_SERVER_ID)
                        .header(AuthHeaders.USER_ID, LEARNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexedDocuments").value(2));

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/search", STUDY_SERVER_ID)
                        .param("q", "homework")
                        .header(AuthHeaders.USER_ID, LEARNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].documentType").value("FAQ"))
                .andExpect(jsonPath("$.results[0].title").value("How do I submit homework?"));

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/search", STUDY_SERVER_ID)
                        .param("q", "slides")
                        .header(AuthHeaders.USER_ID, LEARNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].documentType").value("RESOURCE"))
                .andExpect(jsonPath("$.results[0].title").value("Lecture Slides"));
    }

    @Test
    void unauthorizedCourseContentDoesNotAppearForOtherLearner() throws Exception {
        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/search/reindex", STUDY_SERVER_ID)
                        .header(AuthHeaders.USER_ID, LEARNER_ID))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/search", STUDY_SERVER_ID)
                        .param("q", "homework")
                        .header(AuthHeaders.USER_ID, OTHER_LEARNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
    }
}
