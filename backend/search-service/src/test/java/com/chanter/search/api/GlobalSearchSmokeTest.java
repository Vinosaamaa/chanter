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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalSearchSmokeTest {

    private static final String INTERNAL_TOKEN = "test-internal-service-token-for-search";
    private static final UUID STUDY_SERVER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COURSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LEARNER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_LEARNER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID RESOURCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID INSTRUCTOR_ONLY_RESOURCE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID FAQ_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private com.chanter.search.infra.JdbcSearchIndexRepository index;
    @MockitoBean private com.chanter.search.application.SearchSourceClient sourceClient;

    @MockitoBean
    private CommunityNavigationClient communityNavigationClient;

    @MockitoBean
    private MediaCatalogClient mediaCatalogClient;

    @MockitoBean
    private MessageFaqClient messageFaqClient;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM search_index_entries");
        jdbc.update("DELETE FROM durable_event_cursor");
        when(communityNavigationClient.fetchNavigation(eq(STUDY_SERVER_ID), eq(INSTRUCTOR_ID)))
                .thenReturn(new CommunityNavigationClient.StudyServerNavigation(
                        STUDY_SERVER_ID,
                        "Bootcamp Hub",
                        true,
                        List.of(new CommunityNavigationClient.CourseSummary(COURSE_ID, "Spring Boot"))
                ));

        when(communityNavigationClient.fetchNavigation(eq(STUDY_SERVER_ID), eq(LEARNER_ID)))
                .thenReturn(new CommunityNavigationClient.StudyServerNavigation(
                        STUDY_SERVER_ID,
                        "Bootcamp Hub",
                        false,
                        List.of(new CommunityNavigationClient.CourseSummary(COURSE_ID, "Spring Boot"))
                ));

        when(communityNavigationClient.fetchNavigation(eq(STUDY_SERVER_ID), eq(OTHER_LEARNER_ID)))
                .thenReturn(new CommunityNavigationClient.StudyServerNavigation(
                        STUDY_SERVER_ID,
                        "Bootcamp Hub",
                        false,
                        List.of()
                ));

        when(mediaCatalogClient.listCourseResources(COURSE_ID, INSTRUCTOR_ID))
                .thenReturn(List.of(
                        new MediaCatalogClient.CourseResourceSummary(
                                RESOURCE_ID,
                                COURSE_ID,
                                "Lecture Slides",
                                "slides.pdf"
                        ),
                        new MediaCatalogClient.CourseResourceSummary(
                                INSTRUCTOR_ONLY_RESOURCE_ID,
                                COURSE_ID,
                                "Instructor Notes",
                                "notes.pdf"
                        )
                ));

        when(mediaCatalogClient.listCourseResources(COURSE_ID, LEARNER_ID))
                .thenReturn(List.of(new MediaCatalogClient.CourseResourceSummary(
                        RESOURCE_ID,
                        COURSE_ID,
                        "Lecture Slides",
                        "slides.pdf"
                )));

        when(messageFaqClient.listApprovedFaqs(COURSE_ID, INSTRUCTOR_ID))
                .thenReturn(List.of(new MessageFaqClient.ApprovedFaqSummary(
                        FAQ_ID,
                        COURSE_ID,
                        "How do I submit homework?",
                        "Upload your homework in the resources channel."
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
    void hiddenCandidatesDoNotHideLaterVisibleSearchResults() throws Exception {
        for (int row = 0; row < 51; row++) {
            index.apply(new com.chanter.common.events.SearchChange("RESOURCE", row == 50 ? RESOURCE_ID : new UUID(0, row),
                    STUDY_SERVER_ID, COURSE_ID, null, null, null, "Pagination", "Page proof", "/app/resource", false));
        }
        mockMvc.perform(get("/api/v1/study-servers/{id}/search", STUDY_SERVER_ID).param("q", "Pagination")
                .header(AuthHeaders.USER_ID, LEARNER_ID).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].sourceId").value(RESOURCE_ID.toString()));
    }

    @Test
    void typeFilterAppliesBeforeTheResultLimit() throws Exception {
        for (int row = 0; row < 31; row++) {
            index.apply(new com.chanter.common.events.SearchChange(row == 30 ? "EVENT" : "ANNOUNCEMENT", UUID.randomUUID(),
                    STUDY_SERVER_ID, null, null, null, null, "Filter " + String.format("%03d", row), "Filter proof", "/app/community", false));
        }
        when(sourceClient.currentVisibleHit(org.mockito.ArgumentMatchers.any(), eq(STUDY_SERVER_ID), eq(LEARNER_ID)))
                .thenAnswer(call -> java.util.Optional.of(call.getArgument(0)));
        mockMvc.perform(get("/api/v1/study-servers/{id}/search", STUDY_SERVER_ID).param("q", "Filter").param("type", "EVENT")
                .header(AuthHeaders.USER_ID, LEARNER_ID).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].documentType").value("EVENT"));
    }

    @Test
    void enrolledLearnerCanSearchIndexedResourceAndFaq() throws Exception {
        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/search/reindex", STUDY_SERVER_ID)
                        .header(AuthHeaders.USER_ID, INSTRUCTOR_ID)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexedDocuments").value(3));

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/search", STUDY_SERVER_ID)
                        .param("q", "homework")
                        .header(AuthHeaders.USER_ID, LEARNER_ID)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].documentType").value("FAQ"))
                .andExpect(jsonPath("$.results[0].title").value("How do I submit homework?"));

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/search", STUDY_SERVER_ID)
                        .param("q", "slides")
                        .header(AuthHeaders.USER_ID, LEARNER_ID)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].documentType").value("RESOURCE"))
                .andExpect(jsonPath("$.results[0].title").value("Lecture Slides"));
    }

    @Test
    void learnerCannotReindexStudyServer() throws Exception {
        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/search/reindex", STUDY_SERVER_ID)
                        .header(AuthHeaders.USER_ID, LEARNER_ID)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthorizedCourseContentDoesNotAppearForOtherLearner() throws Exception {
        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/search/reindex", STUDY_SERVER_ID)
                        .header(AuthHeaders.USER_ID, INSTRUCTOR_ID)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/search", STUDY_SERVER_ID)
                        .param("q", "homework")
                        .header(AuthHeaders.USER_ID, OTHER_LEARNER_ID)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void instructorOnlyIndexedResourceDoesNotLeakToLearner() throws Exception {
        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/search/reindex", STUDY_SERVER_ID)
                        .header(AuthHeaders.USER_ID, INSTRUCTOR_ID)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/search", STUDY_SERVER_ID)
                        .param("q", "instructor")
                        .header(AuthHeaders.USER_ID, LEARNER_ID)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void automaticEventsDeduplicateAndOldUpdatesCannotRestoreDeletedContent() throws Exception {
        var change = new com.chanter.common.events.SearchChange("RESOURCE", RESOURCE_ID, null, COURSE_ID,
                null, null, null, "Automatic slides", "automatic content", null, false);
        publish(change, 10, "media");
        publish(change, 10, "media");
        mockMvc.perform(get("/api/v1/study-servers/{id}/search", STUDY_SERVER_ID).param("q", "automatic")
                .header(AuthHeaders.USER_ID, LEARNER_ID).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1));
        publish(new com.chanter.common.events.SearchChange("RESOURCE", RESOURCE_ID, null, COURSE_ID,
                null, null, null, null, null, null, true), 11, "media");
        publish(change, 10, "media");
        publish(change, 12, "media");
        mockMvc.perform(post("/api/v1/study-servers/{id}/search/reindex", STUDY_SERVER_ID)
                .header(AuthHeaders.USER_ID, INSTRUCTOR_ID).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/study-servers/{id}/search", STUDY_SERVER_ID).param("q", "slides")
                .header(AuthHeaders.USER_ID, LEARNER_ID).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void hubEventSearchRequiresCurrentSourcePermissionEvenWithNoCourses() throws Exception {
        UUID id = UUID.randomUUID();
        var hit = new com.chanter.search.domain.SearchHit(com.chanter.search.domain.SearchDocumentType.EVENT,
                null, "", id, "Seminar", "Seminar details", "/app/servers/" + STUDY_SERVER_ID + "/community/events", null, null);
        publish(new com.chanter.common.events.SearchChange("EVENT", id, STUDY_SERVER_ID, null,
                null, null, null, "Seminar", "Seminar details", hit.href(), false), 1, "community");
        when(sourceClient.currentVisibleHit(org.mockito.ArgumentMatchers.any(), eq(STUDY_SERVER_ID), eq(OTHER_LEARNER_ID)))
                .thenReturn(java.util.Optional.of(hit));
        mockMvc.perform(get("/api/v1/study-servers/{id}/search", STUDY_SERVER_ID).param("q", "seminar")
                .header(AuthHeaders.USER_ID, OTHER_LEARNER_ID).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1));
        when(sourceClient.currentVisibleHit(org.mockito.ArgumentMatchers.any(), eq(STUDY_SERVER_ID), eq(OTHER_LEARNER_ID)))
                .thenReturn(java.util.Optional.empty());
        mockMvc.perform(get("/api/v1/study-servers/{id}/search", STUDY_SERVER_ID).param("q", "seminar")
                .header(AuthHeaders.USER_ID, OTHER_LEARNER_ID).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0));
    }

    private void publish(com.chanter.common.events.SearchChange change, long revision, String producer) throws Exception {
        var event = new com.chanter.common.events.DurableEvent(UUID.randomUUID(), 1, producer, revision, change.type(),
                change.type() + ":" + change.sourceId(), mapper.writeValueAsString(change));
        mockMvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(event)))
                .andExpect(status().isNoContent());
    }
}
