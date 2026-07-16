package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.agent.application.ApprovedFaqClient.ApprovedFaqSummary;
import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.application.ResourceIngestionService;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.ChannelCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CohortCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CourseCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.GrantCandidates;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.infra.TestApprovedFaqClient;
import com.chanter.agent.infra.TestCourseResourceCatalogClient;
import com.chanter.agent.infra.TestStudyAssistantGrantCandidatesClient;
import com.chanter.agent.infra.TestStudyServerSaasPlanClient;
import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

@SpringBootTest(properties = "chanter.internal-service-token=test-internal-service-token-for-agent")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalAssistantToolsSmokeTest {

    private static final String INTERNAL_TOKEN = "test-internal-service-token-for-agent";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestStudyAssistantGrantCandidatesClient grantCandidatesClient;

    @Autowired
    private TestCourseResourceCatalogClient courseResourceCatalogClient;

    @Autowired
    private TestApprovedFaqClient approvedFaqClient;

    @Autowired
    private TestStudyServerSaasPlanClient saasPlanClient;

    @Autowired
    private ResourceIngestionService resourceIngestionService;

    @BeforeEach
    void setUp() {
        grantCandidatesClient.clear();
        courseResourceCatalogClient.clear();
        approvedFaqClient.clear();
        saasPlanClient.clear();
    }

    @Test
    void listsToolsAndInvokesGrantScopedTools() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID grantedResourceId = UUID.randomUUID();
        UUID otherResourceId = UUID.randomUUID();

        installAssistant(
                studyServerId,
                instructorUserId,
                learnerUserId,
                channelId,
                courseId,
                cohortId,
                grantedResourceId
        );

        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                grantedResourceId,
                courseId,
                "Homework Guide",
                "homework.txt",
                true
        ));
        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                otherResourceId,
                courseId,
                "Secret Notes",
                "secret.txt",
                true
        ));
        courseResourceCatalogClient.grantViewerAccess(courseId, learnerUserId);

        resourceIngestionService.ingest(
                courseId,
                grantedResourceId,
                "homework.txt",
                """
                        Submit homework before the deadline using the course portal.
                        Late work needs instructor approval.
                        """.getBytes(StandardCharsets.UTF_8)
        );
        resourceIngestionService.ingest(
                courseId,
                otherResourceId,
                "secret.txt",
                "Out of grant secret content.".getBytes(StandardCharsets.UTF_8)
        );

        approvedFaqClient.registerApprovedFaq(
                courseId,
                learnerUserId,
                new ApprovedFaqSummary(
                        UUID.randomUUID(),
                        courseId,
                        "How do I submit homework?",
                        "Use the course portal before the deadline."
                )
        );

        MvcResult listResult = mockMvc.perform(get("/api/v1/internal/assistant-tools")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tools = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools).extracting(node -> node.get("name").asText())
                .contains(
                        "list_granted_resources",
                        "fetch_resource_chunk",
                        "search_course_faq"
                );

        Map<String, Object> listInvoke = invokeBody(
                "list_granted_resources",
                studyServerId,
                courseId,
                learnerUserId,
                channelId,
                Map.of()
        );
        MvcResult listResources = mockMvc.perform(post("/api/v1/internal/assistant-tools/invoke")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(listInvoke)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listed = objectMapper.readTree(listResources.getResponse().getContentAsString())
                .get("result")
                .get("resources");
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("id").asText()).isEqualTo(grantedResourceId.toString());

        Map<String, Object> fetchGranted = invokeBody(
                "fetch_resource_chunk",
                studyServerId,
                courseId,
                learnerUserId,
                null,
                Map.of("resourceId", grantedResourceId.toString(), "chunkIndex", 0)
        );
        MvcResult fetchResult = mockMvc.perform(post("/api/v1/internal/assistant-tools/invoke")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(fetchGranted)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(fetchResult.getResponse().getContentAsString())
                .get("result")
                .get("contentText")
                .asText()).contains("homework");

        Map<String, Object> fetchDenied = invokeBody(
                "fetch_resource_chunk",
                studyServerId,
                courseId,
                learnerUserId,
                null,
                Map.of("resourceId", otherResourceId.toString(), "chunkIndex", 0)
        );
        mockMvc.perform(post("/api/v1/internal/assistant-tools/invoke")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(fetchDenied)))
                .andExpect(status().isForbidden());

        Map<String, Object> faqInvoke = invokeBody(
                "search_course_faq",
                studyServerId,
                courseId,
                learnerUserId,
                null,
                Map.of("query", "homework")
        );
        MvcResult faqResult = mockMvc.perform(post("/api/v1/internal/assistant-tools/invoke")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(faqInvoke)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode faqs = objectMapper.readTree(faqResult.getResponse().getContentAsString())
                .get("result")
                .get("faqs");
        assertThat(faqs).isNotEmpty();
        assertThat(faqs.get(0).get("question").asText()).contains("homework");
    }

    @Test
    void rejectsMissingInternalToken() throws Exception {
        mockMvc.perform(get("/api/v1/internal/assistant-tools"))
                .andExpect(status().isUnauthorized());
    }

    private static Map<String, Object> invokeBody(
            String tool,
            UUID studyServerId,
            UUID courseId,
            UUID viewerUserId,
            UUID channelId,
            Map<String, Object> arguments
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool", tool);
        body.put("studyServerId", studyServerId.toString());
        body.put("courseId", courseId.toString());
        body.put("viewerUserId", viewerUserId.toString());
        if (channelId != null) {
            body.put("channelId", channelId.toString());
        }
        body.put("arguments", arguments);
        return body;
    }

    private void installAssistant(
            UUID studyServerId,
            UUID instructorUserId,
            UUID learnerUserId,
            UUID channelId,
            UUID courseId,
            UUID cohortId,
            UUID courseResourceId
    ) throws Exception {
        grantCandidatesClient.registerGrantCandidates(
                studyServerId,
                instructorUserId,
                new GrantCandidates(
                        studyServerId,
                        List.of(),
                        List.of(new CourseCandidate(
                                courseId,
                                "Spring Boot Foundations",
                                List.of(new CohortCandidate(cohortId, "Summer 2026")),
                                List.of(new ChannelCandidate(channelId, "questions", "TEXT"))
                        ))
                )
        );
        grantCandidatesClient.registerViewerScope(
                studyServerId,
                instructorUserId,
                TestStudyAssistantGrantCandidatesClient.instructorScope(studyServerId)
        );
        grantCandidatesClient.registerViewerScope(
                studyServerId,
                learnerUserId,
                TestStudyAssistantGrantCandidatesClient.learnerScope(
                        studyServerId,
                        Set.of(courseId),
                        Set.of(cohortId),
                        Set.of(channelId)
                )
        );
        saasPlanClient.registerPlan(studyServerId, "STARTER", 100);
        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                courseResourceId,
                courseId,
                "Homework Guide",
                "homework.txt",
                true
        ));

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/study-assistant/install", studyServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "grants", List.of(
                                        Map.of(
                                                "grantType", GrantType.COURSE_CHANNEL.name(),
                                                "grantTargetId", channelId.toString()
                                        ),
                                        Map.of(
                                                "grantType", GrantType.COURSE_RESOURCE.name(),
                                                "grantTargetId", courseResourceId.toString()
                                        )
                                )
                        ))))
                .andExpect(status().isCreated());
    }
}
