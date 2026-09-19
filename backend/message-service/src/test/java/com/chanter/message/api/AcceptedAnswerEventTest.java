package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.AcceptedAnswerStatus;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.DurableOutbox;
import com.chanter.message.application.SupportQuestionRepository;
import com.chanter.message.application.SupportQuestionService;
import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import com.chanter.message.infra.TestCourseChannelAccessClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcceptedAnswerEventTest {
    private static final String TOKEN = "test-internal-service-token-for-message";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private SupportQuestionService questions;
    @Autowired private SupportQuestionRepository repository;
    @Autowired private TestCourseChannelAccessClient access;
    @Autowired private JdbcClient jdbc;
    @MockitoSpyBean private DurableOutbox outbox;

    @Test
    void duplicateAndOutOfOrderDeliveryApplyStatusAndNotificationOnlyOnce() throws Exception {
        var question = question();
        var event = event(question, 20, "AI_ANSWERED");
        deliver(event).andExpect(status().isNoContent());
        deliver(event).andExpect(status().isNoContent());
        deliver(event(question, 19, "AI_LOW_CONFIDENCE")).andExpect(status().isNoContent());
        deliver(event(question, 21, "AI_LOW_CONFIDENCE")).andExpect(status().isNoContent());
        assertThat(current(question)).isEqualTo(SupportQuestionStatus.AI_ANSWERED);
        assertThat(notificationCount(question)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT revision FROM durable_event_cursor WHERE producer='agent' AND aggregate_key=:key")
                .param("key", event.aggregateKey()).query(Long.class).single()).isEqualTo(21);
    }

    @Test
    void deliveryPreservesHumanAndClosedOutcomes() throws Exception {
        for (var status : List.of(SupportQuestionStatus.HUMAN_ANSWERED, SupportQuestionStatus.RESOLVED,
                SupportQuestionStatus.CANCELLED, SupportQuestionStatus.DUPLICATE)) {
            var question = question();
            assertThat(repository.updateStatus(question.id(), SupportQuestionStatus.UNANSWERED, status)).isTrue();
            deliver(event(question, 1, "AI_ANSWERED")).andExpect(status().isNoContent());
            assertThat(current(question)).isEqualTo(status);
            assertThat(notificationCount(question)).isZero();
        }
    }

    @Test
    void currentMembershipAndQuestionAuthorAreRequiredBeforeAnyMutation() throws Exception {
        var question = question();
        UUID stranger = UUID.randomUUID();
        access.grantLearnerPost(question.channelId(), stranger, UUID.randomUUID(), "Questions");
        var foreign = new AcceptedAnswerStatus(UUID.randomUUID(), question.id(), question.channelId(), stranger, "AI_ANSWERED");
        deliver(envelope(foreign, 1)).andExpect(status().isForbidden());
        access.clear();
        access.registerChannel(question.channelId());
        deliver(event(question, 2, "AI_ANSWERED")).andExpect(status().isForbidden());
        assertThat(current(question)).isEqualTo(SupportQuestionStatus.UNANSWERED);
        assertThat(cursorCount(question)).isZero();
        assertThat(notificationCount(question)).isZero();
    }

    @Test
    void statusCursorAndNotificationRollBackTogetherAndSameDeliveryCanRecover() throws Exception {
        var question = question();
        var event = event(question, 1, "AI_ANSWERED");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Synthetic notification append failure");
        }).when(outbox).append(eq("notification"), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> deliver(event)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(current(question)).isEqualTo(SupportQuestionStatus.UNANSWERED);
        assertThat(cursorCount(question)).isZero();
        assertThat(notificationCount(question)).isZero();
        reset(outbox);
        deliver(event).andExpect(status().isNoContent());
        assertThat(current(question)).isEqualTo(SupportQuestionStatus.AI_ANSWERED);
        assertThat(cursorCount(question)).isEqualTo(1);
        assertThat(notificationCount(question)).isEqualTo(1);
    }

    @Test
    void consumerRejectsMissingAuthorityWrongProducerScopeAndUnknownPayloadFields() throws Exception {
        var question = question();
        var valid = event(question, 1, "AI_ANSWERED");
        mvc.perform(post("/api/v1/internal/events").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(valid))).andExpect(status().isUnauthorized());
        for (var invalid : List.of(
                new DurableEvent(valid.id(), 1, "media", 1, valid.kind(), valid.aggregateKey(), valid.payload()),
                new DurableEvent(valid.id(), 1, "agent", 1, "NOTIFICATION", valid.aggregateKey(), valid.payload()),
                new DurableEvent(valid.id(), 1, "agent", 1, valid.kind(), "ACCEPTED_ANSWER:" + UUID.randomUUID(), valid.payload()),
                new DurableEvent(valid.id(), 1, "agent", 1, valid.kind(), valid.aggregateKey(), valid.payload().replace("\"status\":", "\"text\":\"must not be accepted\",\"status\":")),
                new DurableEvent(valid.id(), 1, "agent", 1, valid.kind(), valid.aggregateKey(), valid.payload().replace("AI_ANSWERED", "HUMAN_ANSWERED")),
                new DurableEvent(valid.id(), 1, "agent", 1, valid.kind(), valid.aggregateKey(), valid.payload() + " {}"))) {
            deliver(invalid).andExpect(status().isBadRequest());
        }
        assertThat(current(question)).isEqualTo(SupportQuestionStatus.UNANSWERED);
        assertThat(cursorCount(question)).isZero();
    }

    private SupportQuestion question() {
        UUID channel = UUID.randomUUID(), author = UUID.randomUUID();
        access.grantLearnerPost(channel, author, UUID.randomUUID(), "Questions");
        return questions.postSupportQuestion(channel, author, "A question with an accepted answer", UUID.randomUUID().toString());
    }
    private DurableEvent event(SupportQuestion question, long revision, String status) throws Exception {
        return envelope(new AcceptedAnswerStatus(UUID.randomUUID(), question.id(), question.channelId(), question.senderUserId(), status), revision);
    }
    private DurableEvent envelope(AcceptedAnswerStatus change, long revision) throws Exception {
        return new DurableEvent(UUID.randomUUID(), 1, "agent", revision, AcceptedAnswerStatus.KIND, change.aggregateKey(), mapper.writeValueAsString(change));
    }
    private ResultActions deliver(DurableEvent event) throws Exception {
        return mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(event)));
    }
    private SupportQuestionStatus current(SupportQuestion question) {
        return repository.findByIdAndChannelId(question.channelId(), question.id()).orElseThrow().status();
    }
    private int cursorCount(SupportQuestion question) {
        return jdbc.sql("SELECT COUNT(*) FROM durable_event_cursor WHERE producer='agent' AND aggregate_key=:key")
                .param("key", "ACCEPTED_ANSWER:" + question.id()).query(Integer.class).single();
    }
    private int notificationCount(SupportQuestion question) {
        return jdbc.sql("SELECT COUNT(*) FROM durable_outbox WHERE destination='notification' AND aggregate_key LIKE :key")
                .param("key", "%:" + question.id() + ":SUPPORT_QUESTION_ANSWERED").query(Integer.class).single();
    }
}
