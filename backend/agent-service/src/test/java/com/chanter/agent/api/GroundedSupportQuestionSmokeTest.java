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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroundedSupportQuestionSmokeTest {
    @Autowired private org.springframework.jdbc.core.simple.JdbcClient jdbcClient;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.chanter.agent.application.VectorRetrievalService vectorRetrieval;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void unavailableSemanticEvidenceNeverFallsBackToDownloadedResource(boolean providerFailed) throws Exception {
        UUID server=UUID.randomUUID(),instructor=UUID.randomUUID(),learner=UUID.randomUUID(),channel=UUID.randomUUID(),
                course=UUID.randomUUID(),cohort=UUID.randomUUID(),resource=UUID.randomUUID(),question=UUID.randomUUID();
        installAssistant(server,instructor,learner,channel,course,cohort,resource);
        channelAccessClient.grantLearnerPost(channel,learner,course,server,"questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(question,channel,learner,"When is homework due?"));
        courseResourceCatalogClient.registerResource(new CourseResourceSummary(resource,course,"Homework","homework.txt",true));
        courseResourceCatalogClient.grantViewerAccess(course,learner);
        courseResourceContentClient.registerContent(resource,"Homework is due Friday. Submit homework before Friday.".getBytes(StandardCharsets.UTF_8));
        if(providerFailed) org.mockito.Mockito.doThrow(new com.chanter.agent.application.SemanticRetrievalUnavailableException())
                .when(vectorRetrieval).retrieve(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(course),
                    org.mockito.ArgumentMatchers.eq(learner),org.mockito.ArgumentMatchers.anySet(),org.mockito.ArgumentMatchers.anyInt());
        var result=mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions/{questionId}/assistant-answer",channel,question)
                .header(AuthHeaders.USER_ID,learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-agent"))
                .andExpect(status().isOk()).andReturn();
        var response=objectMapper.readValue(result.getResponse().getContentAsString(),AssistantAnswerResponse.class);
        assertThat(response.confidence()).isEqualTo("LOW");
        assertThat(response.handoffRecommended()).isTrue();
        assertThat(response.sources()).isEmpty();
    }

    @Test void deniedRetrievalDoesNotBecomeASavedLowConfidenceAnswer() throws Exception {
        UUID server=UUID.randomUUID(),instructor=UUID.randomUUID(),learner=UUID.randomUUID(),channel=UUID.randomUUID(),
                course=UUID.randomUUID(),resource=UUID.randomUUID(),question=UUID.randomUUID();
        installAssistant(server,instructor,learner,channel,course,UUID.randomUUID(),resource);
        channelAccessClient.grantLearnerPost(channel,learner,course,server,"questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(question,channel,learner,"A question"));
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(vectorRetrieval).retrieve(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(course),
                    org.mockito.ArgumentMatchers.eq(learner),org.mockito.ArgumentMatchers.anySet(),org.mockito.ArgumentMatchers.anyInt());
        mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions/{questionId}/assistant-answer",channel,question)
                .header(AuthHeaders.USER_ID,learner.toString()).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-agent"))
                .andExpect(status().isForbidden());
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM study_assistant_answers WHERE support_question_id=:id").param("id",question).query(Long.class).single()).isZero();
    }

    private static final com.sun.net.httpserver.HttpServer PROVIDER = fixtureProvider();
    private static final java.util.concurrent.atomic.AtomicInteger PROVIDER_CALLS = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger STATUS_FAILURE_CALLS = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger STATUS_RESPONSE = new java.util.concurrent.atomic.AtomicInteger(503);
    private static final java.security.KeyPair NATIVE_KEYS = nativeKeys();
    private static java.security.KeyPair nativeKeys() {
        try { return java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.chanter.agent.infra.NativeSessionClient nativeSessions;
    @Autowired private com.chanter.common.auth.JwtTokenService jwtTokens;

    @org.springframework.test.context.DynamicPropertySource
    static void localProvider(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:grounded-support-smoke;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        properties.add("chanter.llm.enabled", () -> "true");
        properties.add("chanter.llm.models.fixture.provider", () -> "ollama");
        properties.add("chanter.llm.models.fixture.model", () -> "fixture-local");
        properties.add("chanter.llm.models.fixture.label", () -> "Local fixture");
        properties.add("chanter.llm.models.fixture.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
        properties.add("chanter.native-companion.origin", () -> "https://chanter.example");
        properties.add("chanter.native-companion.private-key-pkcs8", () -> java.util.Base64.getEncoder().encodeToString(NATIVE_KEYS.getPrivate().getEncoded()));
        properties.add("chanter.native-companion.public-key-spki", () -> java.util.Base64.getEncoder().encodeToString(NATIVE_KEYS.getPublic().getEncoded()));
        properties.add("chanter.native-companion.models", () -> "fixture-native");
    }

    private static com.sun.net.httpserver.HttpServer fixtureProvider() {
        try {
            var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/internal/events", exchange -> {
                STATUS_FAILURE_CALLS.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(STATUS_RESPONSE.get(), -1); exchange.close();
            });
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

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.chanter.agent.application.AiGenerationLedger generationLedger;
    @Autowired private com.chanter.agent.application.LlmModelCatalog modelCatalog;
    @Autowired private org.springframework.jdbc.core.simple.JdbcClient nativeJdbc;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate nativeTemplate;
    @Autowired private org.springframework.transaction.PlatformTransactionManager nativeTransactions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.chanter.common.events.DurableOutbox nativeOutbox;

    @Test
    void nativeHttpFlowRechecksEvidenceAndPersistsOnlyValidatedClientQuotations() throws Exception {
        for (String scenario : List.of("status-unavailable", "accepted", "settlement-unavailable", "revoked")) {
            boolean revoke = scenario.equals("revoked"), unavailable = scenario.equals("status-unavailable");
            UUID server = UUID.randomUUID(), instructor = UUID.randomUUID(), learner = UUID.randomUUID();
            UUID channel = UUID.randomUUID(), course = UUID.randomUUID(), resource = UUID.randomUUID(), question = UUID.randomUUID();
            UUID session = UUID.randomUUID(), installation = UUID.randomUUID();
            installAssistant(server, instructor, learner, channel, course, UUID.randomUUID(), resource);
            channelAccessClient.grantLearnerPost(channel, learner, course, server, "questions");
            supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(question, channel, learner, "How does Spring Security work?"));
            courseResourceCatalogClient.grantViewerAccess(course, learner);
            indexSecurityGuide(server,course,resource);
            String bearer = "Bearer " + jwtTokens.createAccessToken(learner, session);
            org.mockito.Mockito.when(nativeSessions.requireActive(bearer, learner)).thenReturn(
                    new com.chanter.common.auth.JwtTokenService.AccessSession(learner, session, java.time.Instant.now().plusSeconds(600)));
            int before = PROVIDER_CALLS.get();
            int statusBefore = STATUS_FAILURE_CALLS.get();
            mockMvc.perform(post("/api/v1/native-companion/pair").header(AuthHeaders.USER_ID, learner).header("Authorization", bearer)
                    .header("Origin", "https://chanter.example").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("installationId", installation, "approved", true))))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/native-companion/status-ticket").header(AuthHeaders.USER_ID, learner).header("Authorization", bearer)
                    .header("Origin", "https://chanter.example").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("installationId", installation))))
                    .andExpect(status().isOk());
            var issued = objectMapper.readTree(mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/native-request", channel, question)
                    .header(AuthHeaders.USER_ID, learner).header("Authorization", bearer).header("Origin", "https://chanter.example")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                            "installationId", installation, "model", "fixture-native", "exportApproved", true))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var signed = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(issued.path("ticket").asText().split("\\.")[0]));
            assertThat(signed.path("evidenceSha256").isArray()).isTrue();
            if (revoke) courseResourceCatalogClient.clear();
            String nativeResultJson = objectMapper.writeValueAsString(Map.of("installationId", installation,
                    "text", "{\"sourceId\":\"S1\",\"quote\":\"Spring Security uses a filter chain.\"}\n",
                    "usage", Map.of("inputTokens", 0, "outputTokens", 0)));
            if (scenario.equals("settlement-unavailable")) org.mockito.Mockito.doThrow(
                    new org.springframework.dao.TransientDataAccessResourceException("synthetic settlement outage"))
                    .when(generationLedger).settle(org.mockito.ArgumentMatchers.eq(UUID.fromString(issued.path("requestId").asText())),
                            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
            var result = mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/native-results/{request}", channel, question, issued.path("requestId").asText())
                    .header(AuthHeaders.USER_ID, learner).header("Authorization", bearer).header("Origin", "https://chanter.example")
                    .contentType(MediaType.APPLICATION_JSON).content(nativeResultJson));
            if (revoke) result.andExpect(status().isForbidden());
            else result.andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.audit.llmProvider").value("codex-native"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.audit.executionProvenance").value("CLIENT_REPORTED"));
            if (scenario.equals("settlement-unavailable")) {
                String savedId = objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("id").asText();
                mockMvc.perform(get("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .header(AuthHeaders.USER_ID, learner).header("Authorization", bearer)).andExpect(status().isOk())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(savedId));
            }
            if (unavailable) {
                String aggregate = com.chanter.common.events.AcceptedAnswerStatus.KIND + ":" + question;
                var destination = Map.of("message", java.net.URI.create("http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/api/v1/internal/events"));
                STATUS_RESPONSE.set(503);
                new com.chanter.common.events.OutboxDispatcher(nativeOutbox, objectMapper, destination,
                        "test-internal-service-token-for-agent").drain();
                assertThat(STATUS_FAILURE_CALLS.get()).isGreaterThan(statusBefore);
                assertThat(nativeJdbc.sql("SELECT status || ':' || last_error FROM durable_outbox WHERE aggregate_key=:key")
                        .param("key", aggregate).query(String.class).single()).isEqualTo("PENDING:HTTP_503");
                var event = objectMapper.readTree(nativeJdbc.sql("SELECT payload FROM durable_outbox WHERE aggregate_key=:key")
                        .param("key", aggregate).query(String.class).single());
                assertThat(event.size()).isEqualTo(5);
                assertThat(event.path("answerId").asText()).isEqualTo(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("id").asText());
                assertThat(event.path("questionId").asText()).isEqualTo(question.toString());
                assertThat(event.path("channelId").asText()).isEqualTo(channel.toString());
                assertThat(event.path("authorId").asText()).isEqualTo(learner.toString());
                assertThat(event.path("status").asText()).isEqualTo("AI_ANSWERED");
                assertThat(nativeJdbc.sql("SELECT outcome FROM native_companion_requests WHERE id=:id")
                        .param("id", UUID.fromString(issued.path("requestId").asText())).query(String.class).single()).isEqualTo("ACCEPTED");
                mockMvc.perform(get("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .header(AuthHeaders.USER_ID, learner).header("Authorization", bearer))
                        .andExpect(status().isOk());
                mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/native-results/{request}", channel, question, issued.path("requestId").asText())
                        .header(AuthHeaders.USER_ID, learner).header("Authorization", bearer).header("Origin", "https://chanter.example")
                        .contentType(MediaType.APPLICATION_JSON).content(nativeResultJson)).andExpect(status().isConflict());
                // An abandoned delivery claim is recovered by a new dispatcher against the same durable rows.
                nativeJdbc.sql("UPDATE durable_outbox SET available_at=:expired WHERE aggregate_key=:key")
                        .param("expired", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)))
                        .param("key", aggregate).update();
                nativeJdbc.sql("UPDATE durable_outbox SET available_at=:later WHERE aggregate_key<>:key AND status='PENDING'")
                        .param("later", java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)))
                        .param("key", aggregate).update();
                var abandoned = nativeOutbox.claim().orElseThrow();
                assertThat(abandoned.event().aggregateKey()).isEqualTo(aggregate);
                nativeJdbc.sql("UPDATE durable_outbox SET lease_until=:expired WHERE id=:id")
                        .param("expired", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)))
                        .param("id", abandoned.event().id()).update();
                STATUS_RESPONSE.set(204);
                var restarted = new com.chanter.common.events.DurableOutbox(nativeTemplate,
                        new org.springframework.transaction.support.TransactionTemplate(nativeTransactions), "agent", java.time.Clock.systemUTC());
                new com.chanter.common.events.OutboxDispatcher(restarted, objectMapper, destination,
                        "test-internal-service-token-for-agent").drain();
                assertThat(nativeJdbc.sql("SELECT status || ':' || payload FROM durable_outbox WHERE id=:id")
                        .param("id", abandoned.event().id()).query(String.class).single()).isEqualTo("DELIVERED:{}");
            }
            assertThat(PROVIDER_CALLS.get()).isEqualTo(before);
            assertThat(generationLedger.summary(server).unknownUsageCount()).isEqualTo(1);
            assertThat(generationLedger.summary(server).accountedTokens()).isEqualTo(40960);
        }
    }

    @Test
    void hostedAnswerRemainsSuccessfulDuringStatusOutageAndRetryDoesNotGenerateOrEnqueueAgain() throws Exception {
        UUID server = UUID.randomUUID(), instructor = UUID.randomUUID(), learner = UUID.randomUUID();
        UUID channel = UUID.randomUUID(), course = UUID.randomUUID(), resource = UUID.randomUUID(), question = UUID.randomUUID();
        installAssistant(server, instructor, learner, channel, course, UUID.randomUUID(), resource);
        channelAccessClient.grantLearnerPost(channel, learner, course, server, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(question, channel, learner, "How does Spring Security work?"));
        courseResourceCatalogClient.grantViewerAccess(course, learner);
        indexSecurityGuide(server,course,resource);
        int providerBefore = PROVIDER_CALLS.get();
        String answerId = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            var response = mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                    .param("modelId", "fixture").header(AuthHeaders.USER_ID, learner)
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String returnedId = objectMapper.readTree(response).path("id").asText();
            if (answerId == null) answerId = returnedId;
            else assertThat(returnedId).isEqualTo(answerId);
            if (attempt == 0) {
                STATUS_RESPONSE.set(503);
                int statusBefore = STATUS_FAILURE_CALLS.get();
                new com.chanter.common.events.OutboxDispatcher(nativeOutbox, objectMapper,
                        Map.of("message", java.net.URI.create("http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/api/v1/internal/events")),
                        "test-internal-service-token-for-agent").drain();
                assertThat(STATUS_FAILURE_CALLS.get()).isGreaterThan(statusBefore);
            }
        }
        assertThat(PROVIDER_CALLS.get() - providerBefore).isEqualTo(1);
        assertThat(nativeJdbc.sql("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=:key")
                .param("key", "ACCEPTED_ANSWER:" + question).query(Integer.class).single()).isEqualTo(1);
        assertThat(nativeJdbc.sql("SELECT status || ':' || last_error FROM durable_outbox WHERE aggregate_key=:key")
                .param("key", "ACCEPTED_ANSWER:" + question).query(String.class).single()).isEqualTo("PENDING:HTTP_503");
    }

    @Test
    void legacyRepairRollbackStillReturnsSavedAnswerAndLaterRepairsOnceWithoutProviderRetry() throws Exception {
        UUID server = UUID.randomUUID(), instructor = UUID.randomUUID(), learner = UUID.randomUUID();
        UUID channel = UUID.randomUUID(), course = UUID.randomUUID(), resource = UUID.randomUUID(), question = UUID.randomUUID();
        installAssistant(server, instructor, learner, channel, course, UUID.randomUUID(), resource);
        channelAccessClient.grantLearnerPost(channel, learner, course, server, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(question, channel, learner, "How does Spring Security work?"));
        courseResourceCatalogClient.grantViewerAccess(course, learner);
        indexSecurityGuide(server,course,resource);
        int providerBefore = PROVIDER_CALLS.get();
        var initial = mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                        .param("modelId", "fixture").header(AuthHeaders.USER_ID, learner)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String answerId = objectMapper.readTree(initial).path("id").asText();
        String key = "ACCEPTED_ANSWER:" + question;
        // Represent an answer saved before atomic outbox integration.
        nativeJdbc.sql("DELETE FROM durable_outbox WHERE aggregate_key=:key").param("key", key).update();
        var failRepair = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(call -> {
            Object event = call.callRealMethod();
            if (failRepair.get()) throw new org.springframework.dao.TransientDataAccessResourceException("synthetic repair failure after append");
            return event;
        }).when(nativeOutbox).append(org.mockito.ArgumentMatchers.eq("message"), org.mockito.ArgumentMatchers.eq("ACCEPTED_ANSWER"),
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.anyString());
        for (int attempt = 0; attempt < 3; attempt++) {
            var response = mockMvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer", channel, question)
                            .param("modelId", "fixture").header(AuthHeaders.USER_ID, learner)
                            .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-agent"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(response).path("id").asText()).isEqualTo(answerId);
            assertThat(nativeJdbc.sql("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=:key").param("key", key)
                    .query(Integer.class).single()).isEqualTo(attempt == 0 ? 0 : 1);
            failRepair.set(false);
        }
        assertThat(PROVIDER_CALLS.get() - providerBefore).isEqualTo(1);
    }

    @Test
    void abandonedProviderAttemptAllowsSourceOnlyRecoveryWithoutAnotherProviderCall() throws Exception {
        UUID server = UUID.randomUUID(), instructor = UUID.randomUUID(), learner = UUID.randomUUID();
        UUID channel = UUID.randomUUID(), course = UUID.randomUUID(), resource = UUID.randomUUID(), question = UUID.randomUUID();
        installAssistant(server, instructor, learner, channel, course, UUID.randomUUID(), resource);
        channelAccessClient.grantLearnerPost(channel, learner, course, server, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(question, channel, learner, "How does Spring Security work?"));
        courseResourceCatalogClient.grantViewerAccess(course, learner);
        indexSecurityGuide(server,course,resource);
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
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.audit.llmUsed").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.audit.executionProvenance").value("NOT_USED"));
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
        indexSecurityGuide(server,course,resource);
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
        assertThat(answer.audit().executionProvenance()).isEqualTo("SERVER_OBSERVED");
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
        jdbcClient.sql("UPDATE resource_index_lifecycle SET study_server_id=:server WHERE resource_id=:id")
                .param("server",studyServerId).param("id",courseResourceId).update();
        courseResourceCatalogClient.registerResource(new CourseResourceSummary(courseResourceId,courseId,
                "Spring Security Guide","spring-security-guide.md",true,studyServerId,null,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(guideBytes))));

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

    private void indexSecurityGuide(UUID server,UUID course,UUID resource) throws Exception {
        byte[] bytes="Spring Security uses a filter chain.".getBytes(StandardCharsets.UTF_8);
        courseResourceContentClient.registerContent(resource,bytes);
        resourceIngestionService.ingest(course,resource,"spring-security-guide.md",bytes);
        jdbcClient.sql("UPDATE resource_index_lifecycle SET study_server_id=:server WHERE resource_id=:id")
                .param("server",server).param("id",resource).update();
        courseResourceCatalogClient.registerResource(new CourseResourceSummary(resource,course,"Spring Security Guide",
                "spring-security-guide.md",true,server,null,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))));
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
