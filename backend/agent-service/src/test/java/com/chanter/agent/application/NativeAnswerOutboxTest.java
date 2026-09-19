package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerSource;
import com.chanter.common.events.DurableOutbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class NativeAnswerOutboxTest {
    @Autowired private StudyAssistantAnswerPersistenceService persistence;
    @Autowired private StudyAssistantAnswerRepository answers;
    @Autowired private JdbcClient jdbc;
    @MockitoBean private AiQuotaEnforcementService quota;
    @MockitoSpyBean private DurableOutbox outbox;

    @Test
    void failureAfterAppendRollsBackAnswerAuditSourcesAndEventTogether() {
        var answer = new StudyAssistantAnswer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Authorized question", "Authorized quotation", AnswerConfidence.HIGH, false,
                List.of(new StudyAssistantAnswerSource(UUID.randomUUID(), UUID.randomUUID(), "Resource", "Authorized quotation")), Instant.now());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Synthetic transaction failure after append");
        }).when(outbox).append(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> persistence.saveNativeAnswer(answer, InvocationType.GROUNDED_ANSWER, "fixture-native"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(answers.findBySupportQuestionId(answer.supportQuestionId())).isEmpty();
        assertThat(answers.findAuditByAnswerId(answer.id())).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM study_assistant_answer_sources WHERE answer_id=:id")
                .param("id", answer.id()).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=:key")
                .param("key", "ACCEPTED_ANSWER:" + answer.supportQuestionId()).query(Integer.class).single()).isZero();
    }
}
