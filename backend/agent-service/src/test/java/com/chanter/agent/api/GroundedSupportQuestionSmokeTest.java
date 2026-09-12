package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.ChannelCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CohortCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CourseCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.GrantCandidates;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.application.ResourceIngestionService;
import com.chanter.agent.infra.TestCourseResourceCatalogClient;
import com.chanter.agent.infra.TestCourseResourceContentClient;
import com.chanter.agent.infra.TestStudyAssistantGrantCandidatesClient;
import com.chanter.agent.infra.TestStudyServerSaasPlanClient;
import com.chanter.agent.infra.TestSupportQuestionChannelAccessClient;
import com.chanter.agent.infra.TestSupportQuestionClient;
import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroundedSupportQuestionSmokeTest {

    private static final com.sun.net.httpserver.HttpServer PROVIDER = fixtureProvider();
    private static final java.util.concurrent.atomic.AtomicInteger PROVIDER_CALLS = new java.util.concurrent.atomic.AtomicInteger();

    @org.springframework.test.context.DynamicPropertySource
    static void localProvider(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("chanter.llm.enabled", () -> "true");
        properties.add("chanter.llm.models.fixture.provider", () -> "ollama");
        properties.add("chanter.llm.models.fixture.model", () -> "fixture-local");
        properties.add("chanter.llm.models.fixture.label", () -> "Local fixture");
        properties.add("chanter.llm.models.fixture.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
    }

    private static com.sun.net.httpserver.HttpServer fixtureProvider() {
        try {
            var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                PROVIDER_CALLS.incrementAndGet();
                String quote = "{\"sourceId\":\"S1\",\"quote\":\"Spring Security uses a filter chain.\"}\n";
                var mapper = new ObjectMapper();
                byte[] response = (mapper.writeValueAsString(Map.of("model", "fixture-local", "message", Map.of("content", quote), "done", false)) + "\n"
                        + mapper.writeValueAsString(Map.of("model", "fixture-local", "done", true, "done_reason", "stop", "prompt_eval_count", 100, "eval_count", 20)) + "\n").getBytes(StandardCharsets.UTF_8);
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException failure) { throw new ExceptionInInitializerError(failure); }
    }

    @org.junit.jupiter.api.AfterAll
    static void closeProvider() { PROVIDER.stop(0); }

    @Autowired private com.chanter.agent.application.AiGenerationLedger generationLedger;
    @Autowired private com.chanter.agent.application.LlmModelCatalog modelCatalog;

    @Test
    void abandonedProviderAttemptAllowsSourceOnlyRecoveryWithoutAnotherProviderCall() throws Exception {
        UUID server = UUID.randomUUID(), instructor = UUID.randomUUID(), learner = UUID.randomUUID();
        UUID channel = UUID.randomUUID(), course = UUID.randomUUID(), resource = UUID.randomUUID(), question = UUID.randomUUID();
        installAssistant(server, instructor, learner, channel, course, UUID.randomUUID(), resource);
        channelAccessClient.grantLearnerPost(channel, learner, course, server, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(question, channel, learner, "How does Spring Security work?"));
        courseResourceCatalogClient.grantViewerAccess(course, learner);
        courseResourceContentClient.registerContent(resource, "Spring Security uses a filter chain.".getBytes(StandardCharsets.UTF_8));
        generationLedger.reserve(server, question, learner, "fixture", modelCatalog.definition("fixture"));
        int before = PROVIDER_CALLS.get();

        MvcResult result = mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer/stream", channel, question)
                        .param("modelId", "fixture")
                        .header(AuthHeaders.USER_ID, learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        result.getAsyncResult(10_000);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result)).andExpect(status().isOk());
        assertThat(result.getResponse().getContentAsString()).contains("GENERATION_ALREADY_ATTEMPTED", "may be unknown", "Source only");

        mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .param("modelId", "source-only")
                        .header(AuthHeaders.USER_ID, learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.audit.llmUsed").value(false));
        assertThat(PROVIDER_CALLS.get()).isEqualTo(before);
        assertThat(generationLedger.summary(server).requestCount()).isEqualTo(1);
        assertThat(generationLedger.summary(server).unknownUsageCount()).isEqualTo(1);
    }

    @Test
    void selectedLocalProviderStreamsValidatedEvidenceAndPersistsMeasuredUsage() throws Exception {
        UUID server = UUID.randomUUID(), instructor = UUID.randomUUID(), learner = UUID.randomUUID();
        UUID channel = UUID.randomUUID(), course = UUID.randomUUID(), resource = UUID.randomUUID(), question = UUID.randomUUID();
        installAssistant(server, instructor, learner, channel, course, UUID.randomUUID(), resource);
        channelAccessClient.grantLearnerPost(channel, learner, course, server, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(question, channel, learner, "How does Spring Security work?"));
        courseResourceCatalogClient.grantViewerAccess(course, learner);
        courseResourceContentClient.registerContent(resource, "Spring Security uses a filter chain.".getBytes(StandardCharsets.UTF_8));
        int before = PROVIDER_CALLS.get();

        MvcResult result = mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer/stream", channel, question)
                        .param("modelId", "fixture")
                        .header(AuthHeaders.USER_ID, learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        result.getAsyncResult(10_000);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result)).andExpect(status().isOk());
        assertThat(PROVIDER_CALLS.get() - before).isEqualTo(1);
        String events = result.getResponse().getContentAsString();
        assertThat(events).contains("event:token", "Spring Security", "event:complete");
        AssistantAnswerResponse answer = objectMapper.readValue(mockMvc.perform(get("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .header(AuthHeaders.USER_ID, learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), AssistantAnswerResponse.class);
        assertThat(answer.audit().llmUsed()).isTrue();
        assertThat(answer.audit().llmProvider()).isEqualTo("ollama");
        assertThat(answer.sources()).hasSize(1);
        // Instructor grant visibility does not require learner enrollment or a personal assistant installation.
        channelAccessClient.grantInstructorView(channel, instructor, course, server, "questions");
        supportQuestionClient.grantViewerAccess(question, instructor);
        courseResourceCatalogClient.grantViewerAccess(course, instructor);
        mockMvc.perform(get("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .header(AuthHeaders.USER_ID, instructor.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(answer.id().toString()));
        UUID teachingAssistant = UUID.randomUUID();
        grantCandidatesClient.registerViewerScope(server, teachingAssistant,
                new com.chanter.agent.application.StudyAssistantGrantCandidatesClient.ViewerScope(server, false, Set.of(), Set.of(), Set.of(channel)));
        channelAccessClient.grantInstructorView(channel, teachingAssistant, course, server, "questions");
        supportQuestionClient.grantViewerAccess(question, teachingAssistant);
        courseResourceCatalogClient.grantViewerAccess(course, teachingAssistant);
        mockMvc.perform(get("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .header(AuthHeaders.USER_ID, teachingAssistant.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk());
        assertThat(generationLedger.summary(server).accountedTokens()).isEqualTo(120);
        mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .param("modelId", "removed-model").param("answerMode", "grounded-explanation")
                        .header(AuthHeaders.USER_ID, learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(answer.id().toString()));
        assertThat(PROVIDER_CALLS.get() - before).isEqualTo(1);
        mockMvc.perform(get("/api/v1/study-servers/{server}/ai-usage-metrics", server)
                        .header(AuthHeaders.USER_ID, instructor.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.generationUsage.accountedTokens").value(120))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.generationUsage.unknownUsageCount").value(0));
        mockMvc.perform(get("/api/v1/study-servers/{server}/ai-usage-metrics", server)
                        .header(AuthHeaders.USER_ID, learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isForbidden());

        courseResourceCatalogClient.clear();
        courseResourceCatalogClient.grantViewerAccess(course, learner);
        courseResourceCatalogClient.grantViewerAccess(course, teachingAssistant);
        mockMvc.perform(get("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .header(AuthHeaders.USER_ID, learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .header(AuthHeaders.USER_ID, teachingAssistant.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isForbidden());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestStudyAssistantGrantCandidatesClient grantCandidatesClient;

    @Autowired
    private TestCourseResourceCatalogClient courseResourceCatalogClient;

    @Autowired
    private TestCourseResourceContentClient courseResourceContentClient;

    @Autowired
    private TestSupportQuestionChannelAccessClient channelAccessClient;

    @Autowired
    private TestSupportQuestionClient supportQuestionClient;

    @Autowired
    private TestStudyServerSaasPlanClient saasPlanClient;

    @Autowired
    private ResourceIngestionService resourceIngestionService;

    @BeforeEach
    void setUp() {
        grantCandidatesClient.clear();
        courseResourceCatalogClient.clear();
        courseResourceContentClient.clear();
        channelAccessClient.clear();
        supportQuestionClient.clear();
        saasPlanClient.clear();
    }

    @Test
    void learnerGetsGroundedAnswerWithSources() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID courseResourceId = UUID.randomUUID();
        UUID supportQuestionId = UUID.randomUUID();

        installAssistant(
                studyServerId,
                instructorUserId,
                learnerUserId,
                channelId,
                courseId,
                cohortId,
                courseResourceId
        );

        channelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, studyServerId, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                supportQuestionId,
                channelId,
                learnerUserId,
                "How do I configure Spring Security filters?"
        ));

        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                courseResourceId,
                courseId,
                "Spring Security Guide",
                "spring-security-guide.md",
                true
        ));
        courseResourceCatalogClient.grantViewerAccess(courseId, learnerUserId);
        byte[] guideBytes = """
                Spring Security uses a filter chain. Configure HttpSecurity to add authentication \
                and authorization rules for your endpoints.
                """.getBytes(StandardCharsets.UTF_8);
        courseResourceContentClient.registerContent(courseResourceId, guideBytes);
        resourceIngestionService.ingest(courseId, courseResourceId, "spring-security-guide.md", guideBytes);

        MvcResult result = mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        channelId,
                        supportQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk())
                .andReturn();

        AssistantAnswerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AssistantAnswerResponse.class
        );

        assertThat(response.confidence()).isEqualTo("HIGH");
        assertThat(response.handoffRecommended()).isFalse();
        assertThat(response.supportQuestionStatus()).isEqualTo("AI_ANSWERED");
        assertThat(response.answerBody()).contains("Spring Security Guide");
        assertThat(response.answerBody()).contains("chars ");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().resourceId()).isEqualTo(courseResourceId);
        assertThat(response.sources().getFirst().excerpt()).contains("offsets");

        MvcResult reloadResult = mockMvc.perform(get(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                            channelId,
                            supportQuestionId
                        )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk())
                .andReturn();
        AssistantAnswerResponse reloaded = objectMapper.readValue(
                reloadResult.getResponse().getContentAsString(),
                AssistantAnswerResponse.class
        );

        assertThat(reloaded.id()).isEqualTo(response.id());
        assertThat(reloaded.sources()).isEqualTo(response.sources());
    }

    @Test
    void unavailableGrantedResourceFallsBackToLowConfidenceAnswerAndHandoff() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID courseResourceId = UUID.randomUUID();
        UUID supportQuestionId = UUID.randomUUID();

        installAssistant(
                studyServerId,
                instructorUserId,
                learnerUserId,
                channelId,
                courseId,
                cohortId,
                courseResourceId
        );

        channelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, studyServerId, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                supportQuestionId,
                channelId,
                learnerUserId,
                "What is the weather in Tokyo?"
        ));

        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                courseResourceId,
                courseId,
                "Spring Security Guide",
                "spring-security-guide.md",
                true
        ));
        courseResourceCatalogClient.grantViewerAccess(courseId, learnerUserId);
        courseResourceContentClient.registerUnavailable(courseResourceId);

        MvcResult result = mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        channelId,
                        supportQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk())
                .andReturn();

        AssistantAnswerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AssistantAnswerResponse.class
        );

        assertThat(response.confidence()).isEqualTo("LOW");
        assertThat(response.handoffRecommended()).isTrue();
        assertThat(response.supportQuestionStatus()).isEqualTo("AI_LOW_CONFIDENCE");
        assertThat(response.sources()).isEmpty();
        assertThat(response.answerBody()).contains("approved material");
    }

    @Test
    void ungrantedChannelReturnsForbidden() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID grantedChannelId = UUID.randomUUID();
        UUID deniedChannelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID courseResourceId = UUID.randomUUID();
        UUID supportQuestionId = UUID.randomUUID();

        installAssistant(
                studyServerId,
                instructorUserId,
                learnerUserId,
                grantedChannelId,
                courseId,
                cohortId,
                courseResourceId
        );

        channelAccessClient.grantLearnerPost(deniedChannelId, learnerUserId, courseId, studyServerId, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                supportQuestionId,
                deniedChannelId,
                learnerUserId,
                "How do I configure Spring Security filters?"
        ));

        mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        deniedChannelId,
                        supportQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isForbidden());
    }

    @Test
    void uninstalledAssistantReturnsNotFound() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID supportQuestionId = UUID.randomUUID();

        grantCandidatesClient.registerViewerScope(
                studyServerId,
                learnerUserId,
                TestStudyAssistantGrantCandidatesClient.learnerScope(
                        studyServerId,
                        Set.of(courseId),
                        Set.of(),
                        Set.of(channelId)
                )
        );

        channelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, studyServerId, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                supportQuestionId,
                channelId,
                learnerUserId,
                "How do I configure Spring Security filters?"
        ));

        mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        channelId,
                        supportQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isNotFound());
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
                "Spring Security Guide",
                "spring-security-guide.md",
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
