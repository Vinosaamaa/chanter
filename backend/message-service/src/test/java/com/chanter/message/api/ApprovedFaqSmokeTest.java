package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.infra.TestCourseChannelAccessClient;
import com.chanter.message.infra.TestCourseResourceAccessClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class ApprovedFaqSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestCourseChannelAccessClient courseChannelAccessClient;

    @Autowired
    private TestCourseResourceAccessClient courseResourceAccessClient;

    @BeforeEach
    void setUp() {
        courseChannelAccessClient.clear();
        courseResourceAccessClient.clear();
    }

    @Test
    void instructorCanPromoteRepeatedSupportQuestionsToApprovedFaqAndLearnerCanSearch() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");
        courseChannelAccessClient.grantInstructorView(channelId, instructorUserId, courseId, "questions");
        courseResourceAccessClient.grantInstructorUpload(courseId, instructorUserId);
        courseResourceAccessClient.grantLearnerView(courseId, learnerUserId);

        SupportQuestionResponse firstQuestion = postSupportQuestion(
                channelId,
                learnerUserId,
                "How do I configure Spring Security?"
        );
        SupportQuestionResponse secondQuestion = postSupportQuestion(
                channelId,
                learnerUserId,
                "How can I configure Spring Security filters?"
        );

        MvcResult candidatesResult = mockMvc.perform(get("/api/v1/course-channels/{channelId}/faq-candidates", channelId)
                        .header(AuthHeaders.USER_ID, instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        FaqCandidateListResponse candidates = objectMapper.readValue(
                candidatesResult.getResponse().getContentAsString(),
                FaqCandidateListResponse.class
        );

        assertThat(candidates.faqCandidates()).hasSize(1);
        assertThat(candidates.faqCandidates().getFirst().supportQuestions()).hasSize(2);

        MvcResult createResult = mockMvc.perform(post("/api/v1/courses/{courseId}/approved-faqs", courseId)
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channelId", channelId.toString(),
                                "question", "How do I configure Spring Security filters?",
                                "answer", "Configure HttpSecurity to add authentication and authorization rules.",
                                "sourceSupportQuestionIds", List.of(
                                        firstQuestion.id().toString(),
                                        secondQuestion.id().toString()
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        ApprovedFaqResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                ApprovedFaqResponse.class
        );

        assertThat(created.courseId()).isEqualTo(courseId);
        assertThat(created.approvedByUserId()).isEqualTo(instructorUserId);
        assertThat(createResult.getResponse().getHeader("Location"))
                .contains("/api/v1/courses/" + courseId + "/approved-faqs/" + created.id());

        MvcResult listResult = mockMvc.perform(get("/api/v1/courses/{courseId}/approved-faqs", courseId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        ApprovedFaqListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                ApprovedFaqListResponse.class
        );

        assertThat(listed.approvedFaqs()).containsExactly(created);

        MvcResult searchResult = mockMvc.perform(get("/api/v1/courses/{courseId}/approved-faqs/search", courseId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .param("query", "authentication"))
                .andExpect(status().isOk())
                .andReturn();
        ApprovedFaqListResponse searched = objectMapper.readValue(
                searchResult.getResponse().getContentAsString(),
                ApprovedFaqListResponse.class
        );

        assertThat(searched.approvedFaqs()).extracting(ApprovedFaqResponse::id).containsExactly(created.id());
    }

    @Test
    void learnerCannotViewFaqCandidates() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/faq-candidates", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthorizedUserCannotListApprovedFaqs() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        courseResourceAccessClient.registerCourse(courseId);

        mockMvc.perform(get("/api/v1/courses/{courseId}/approved-faqs", courseId)
                        .header(AuthHeaders.USER_ID, strangerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    private SupportQuestionResponse postSupportQuestion(UUID channelId, UUID senderUserId, String body)
            throws Exception {
        MvcResult postResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, senderUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", body,
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(postResult.getResponse().getContentAsString(), SupportQuestionResponse.class);
    }
}
