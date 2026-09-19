package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanter.agent.application.GroundedSupportQuestionService.NativeEvidence;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.infra.NativeSessionClient;
import com.chanter.common.auth.JwtTokenService.AccessSession;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = { "chanter.native-companion.origin=https://chanter.example", "chanter.native-companion.models=fixture-model" })
@ActiveProfiles("test")
class NativeCompanionServiceTest {
    @Autowired NativeCompanionService companion;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AiGenerationLedger ledger;
    @Autowired JdbcClient jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.chanter.agent.infra.NativeRequestRepository requests;
    @MockitoBean NativeSessionClient sessions;
    @MockitoBean GroundedSupportQuestionService questions;
    private UUID user, session, installation, channel, question, server;
    private NativeEvidence evidence;
    private StudyAssistantAnswer saved;
    private static final String AUTH = "Bearer synthetic-native-fixture";
    private static final String ORIGIN = "https://chanter.example";
    private static final String QUOTE = "The approved course evidence is retained exactly.";
    private static final String RESULT = "{\"sourceId\":\"S1\",\"quote\":\"" + QUOTE + "\"}";
    private static final KeyPair KEYS = keys();
    private static KeyPair keys() {
        try { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    @DynamicPropertySource static void signing(DynamicPropertyRegistry registry) {
        registry.add("chanter.native-companion.private-key-pkcs8", () -> Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()));
        registry.add("chanter.native-companion.public-key-spki", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
    }
    @BeforeEach void fixture() {
        user = UUID.randomUUID(); session = UUID.randomUUID(); installation = UUID.randomUUID();
        channel = UUID.randomUUID(); question = UUID.randomUUID(); server = UUID.randomUUID();
        jdbc.sql("INSERT INTO study_assistant_installs(id,study_server_id,installed_by_user_id,installed_at) VALUES(:id,:server,:user,:at)")
                .param("id", UUID.randomUUID()).param("server", server).param("user", user).param("at", OffsetDateTime.now()).update();
        evidence = new NativeEvidence(server, UUID.randomUUID(), "What does the course say?", List.of(new SourceCitation(UUID.randomUUID(), "Course", QUOTE)));
        saved = new StudyAssistantAnswer(UUID.randomUUID(), question, channel, server, user, evidence.question(), QUOTE, AnswerConfidence.HIGH, false, List.of(), Instant.now());
        when(sessions.requireActive(AUTH, user)).thenReturn(new AccessSession(user, session, Instant.now().plusSeconds(300)));
        when(questions.prepareNativeEvidence(eq(channel), eq(question), eq(user), any())).thenReturn(evidence);
        when(questions.acceptNativeAnswer(eq(channel), eq(question), eq(user), any(), any(), eq("fixture-model"), any())).thenReturn(saved);
    }
    private NativeCompanionService.Issued issue() { return companion.issue(channel, question, user, AUTH, ORIGIN, installation, "fixture-model", true); }
    private StudyAssistantAnswer accept(UUID request, String text) { return companion.accept(channel, question, request, user, AUTH, ORIGIN, installation, text, 0, 0); }

    @Test void acceptedClientZeroNeverRefundsReservationAndResultCannotBeReplayed() {
        var issued = issue();
        assertThat(issued.prompt()).contains(QUOTE);
        assertThat(accept(issued.requestId(), RESULT)).isEqualTo(saved);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(40960);
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        var receipt = jdbc.sql("SELECT outcome,client_input_tokens,provenance,evidence_json FROM native_companion_requests WHERE id=:id")
                .param("id", issued.requestId()).query().singleRow();
        assertThat(receipt.get("outcome")).isEqualTo("ACCEPTED");
        assertThat(receipt.get("client_input_tokens")).isEqualTo(0);
        assertThat(receipt.get("provenance")).isEqualTo("native-client-report");
        assertThat(receipt.get("evidence_json")).isNull();
        assertThatThrownBy(() -> accept(issued.requestId(), RESULT)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }
    @Test void fabricatedQuotationCannotBecomeAnAnswerOrReleaseAnUncertainAttempt() {
        var issued = issue();
        assertThatThrownBy(() -> accept(issued.requestId(), "{\"sourceId\":\"S1\",\"quote\":\"This fabricated quote is absent from the course.\"}"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verify(questions, never()).acceptNativeAnswer(any(), any(), any(), any(), any(), any(), any());
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        assertThatThrownBy(this::issue).isInstanceOf(AiGenerationLedger.AttemptConflict.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { true, false })
    void savedAnswerRemainsSuccessfulWhenSettlementOrRequestFinalizationFails(boolean ledgerFailure) {
        var issued = issue();
        var failure = new org.springframework.dao.TransientDataAccessResourceException("synthetic settlement failure");
        if (ledgerFailure) doThrow(failure).when(ledger).settle(eq(issued.requestId()), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        else doThrow(failure).when(requests).finish(issued.requestId(), true, 0, 0);
        assertThat(accept(issued.requestId(), RESULT)).isEqualTo(saved);
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(40960);
        assertThat(jdbc.sql("SELECT outcome FROM native_companion_requests WHERE id=:id").param("id", issued.requestId())
                .query(String.class).single()).isEqualTo(ledgerFailure ? "ACCEPTED" : "ACCEPTING");
        assertThatThrownBy(() -> accept(issued.requestId(), RESULT)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(this::issue).isInstanceOf(AiGenerationLedger.AttemptConflict.class);
        verify(questions, org.mockito.Mockito.times(1)).acceptNativeAnswer(any(), any(), any(), any(), any(), any(), any());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"TIMED_OUT,504", "CANCELLED,408", "REFUSED,422", "INVALID_RESPONSE,400", "UNAVAILABLE,503", "RATE_LIMITED,429", "LIMIT_EXCEEDED,429"})
    void validationFailurePreservesItsOutcomeAndCannotReleaseOrReplayTheAttempt(LlmProviderException.Outcome outcome, int status) {
        var issued = issue();
        doThrow(new LlmProviderException(outcome)).when(questions).requireNativeEvidenceCurrent(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> accept(issued.requestId(), RESULT)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(status));
        verify(questions, never()).acceptNativeAnswer(any(), any(), any(), any(), any(), any(), any());
        assertThat(jdbc.sql("SELECT outcome FROM ai_generation_usage WHERE id=:id").param("id", issued.requestId())
                .query(String.class).single()).isEqualTo(outcome.name());
        assertThat(jdbc.sql("SELECT outcome FROM native_companion_requests WHERE id=:id").param("id", issued.requestId())
                .query(String.class).single()).isEqualTo("REJECTED");
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(40960);
        assertThatThrownBy(() -> accept(issued.requestId(), RESULT)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        org.mockito.Mockito.doNothing().when(questions).requireNativeEvidenceCurrent(any(), any(), any(), any(), any());
        assertThatThrownBy(this::issue).isInstanceOf(AiGenerationLedger.AttemptConflict.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,400", "false,400", "true,403", "false,403", "true,504", "false,504"})
    void rejectedResultKeepsItsOriginalStatusWhenSettlementAlsoFails(boolean ledgerFailure, int status) {
        var issued = issue();
        var failure = new org.springframework.dao.TransientDataAccessResourceException("synthetic settlement failure");
        if (ledgerFailure) doThrow(failure).when(ledger).settle(eq(issued.requestId()), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        else doThrow(failure).when(requests).finish(issued.requestId(), false, 0, 0);
        if (status != 400) doThrow(status == 403 ? new ResponseStatusException(HttpStatus.FORBIDDEN)
                : new LlmProviderException(LlmProviderException.Outcome.TIMED_OUT))
                .when(questions).requireNativeEvidenceCurrent(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> accept(issued.requestId(), status == 400 ? "invalid quotation" : RESULT))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(status));
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(40960);
        assertThatThrownBy(() -> accept(issued.requestId(), RESULT)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        org.mockito.Mockito.doNothing().when(questions).requireNativeEvidenceCurrent(any(), any(), any(), any(), any());
        assertThatThrownBy(this::issue).isInstanceOf(AiGenerationLedger.AttemptConflict.class);
        verify(questions, never()).acceptNativeAnswer(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void revokedSessionAndMissingExportApprovalPreventEvidenceRelease() {
        assertThatThrownBy(() -> companion.issue(channel, question, user, AUTH, ORIGIN, installation, "fixture-model", false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        when(sessions.requireActive(AUTH, user)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(this::issue).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        verify(questions, never()).prepareNativeEvidence(any(), any(), any(), any());
        assertThat(ledger.summary(server).requestCount()).isZero();
    }
    @Test void sessionOrInstallationChangeCannotAcceptTheOriginalRequest() {
        var issued = issue();
        assertThatThrownBy(() -> companion.accept(channel, question, issued.requestId(), user, AUTH, ORIGIN, UUID.randomUUID(), RESULT, null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        when(sessions.requireActive(AUTH, user)).thenReturn(new AccessSession(user, UUID.randomUUID(), Instant.now().plusSeconds(300)));
        assertThatThrownBy(() -> accept(issued.requestId(), RESULT)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(questions, never()).acceptNativeAnswer(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void authorizationRevokedAfterIssuanceRejectsOutputAndKeepsItsAttempt() {
        var issued = issue();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(questions).requireNativeEvidenceCurrent(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> accept(issued.requestId(), RESULT)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(questions, never()).acceptNativeAnswer(any(), any(), any(), any(), any(), any(), any());
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(40960);
    }
    @Test void concurrentNativeIssuanceUsesTheSharedOneAttemptReservation() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt = () -> { start.await(); try { issue(); return true; } catch (AiGenerationLedger.AttemptConflict rejected) { return false; } };
            var first = executor.submit(attempt); var second = executor.submit(attempt); start.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
            assertThat(ledger.summary(server).requestCount()).isEqualTo(1);
        }
    }
    @Test void concurrentResultAcceptancePersistsAtMostOnce() throws Exception {
        var issued = issue(); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt = () -> { start.await(); try { accept(issued.requestId(), RESULT); return true; }
                catch (ResponseStatusException rejected) { assertThat(rejected.getStatusCode().value()).isEqualTo(409); return false; } };
            var first = executor.submit(attempt); var second = executor.submit(attempt); start.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
            verify(questions, org.mockito.Mockito.times(1)).acceptNativeAnswer(any(), any(), any(), any(), any(), any(), any());
        }
    }
    @Test void expiredAndAbandonedRequestsEraseEvidenceWithoutReleasingTheReservation() {
        var issued = issue();
        jdbc.sql("UPDATE native_companion_requests SET outcome='ACCEPTING',accept_until=:past WHERE id=:id")
                .param("past", OffsetDateTime.now().minusMinutes(2)).param("id", issued.requestId()).update();
        requests.expire();
        var row = jdbc.sql("SELECT outcome,evidence_json FROM native_companion_requests WHERE id=:id")
                .param("id", issued.requestId()).query().singleRow();
        assertThat(row.get("outcome")).isEqualTo("ACCEPTING"); assertThat(row.get("evidence_json")).isNull();
        // Redaction cannot falsely terminate an in-flight acceptance; its eventual settlement remains authoritative.
        requests.finish(issued.requestId(), true, null, null);
        assertThat(jdbc.sql("SELECT outcome FROM native_companion_requests WHERE id=:id").param("id", issued.requestId()).query(String.class).single()).isEqualTo("ACCEPTED");
        assertThatThrownBy(() -> accept(issued.requestId(), RESULT)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(this::issue).isInstanceOf(AiGenerationLedger.AttemptConflict.class);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(40960);
    }
}
