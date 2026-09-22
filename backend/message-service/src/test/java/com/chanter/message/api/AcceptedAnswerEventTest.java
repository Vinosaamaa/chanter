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
import com.chanter.common.events.AnswerRetraction;
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
    @Test void answerNotificationVisibilityRequiresCurrentQuestionAccessAndTheExactBinding() throws Exception {
        var question=question(); var event=event(question,1,"AI_ANSWERED"); deliver(event).andExpect(status().isNoContent());
        var change=mapper.readValue(event.payload(),AcceptedAnswerStatus.class);
        String route="/api/v1/course-channels/"+question.channelId()+"/accepted-answers/"+change.answerId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route)).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).header(AuthHeaders.USER_ID,question.senderUserId())
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).header(AuthHeaders.USER_ID,UUID.randomUUID())
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)).andExpect(status().isForbidden());
        deliver(retraction(change,2)).andExpect(status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).header(AuthHeaders.USER_ID,question.senderUserId())
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)).andExpect(status().isNotFound());
    }
    @Test void retractionWinsOverQueuedAndClaimedAcceptanceButPreservesALaterAnswer() throws Exception {
        for(boolean deliveredFirst:List.of(false,true)) {
            var question=question(); var accepted=event(question,10,"AI_ANSWERED");
            var change=mapper.readValue(accepted.payload(),AcceptedAnswerStatus.class);
            if(deliveredFirst) deliver(accepted).andExpect(status().isNoContent());
            var retraction=retraction(change,11);
            deliver(retraction).andExpect(status().isNoContent());
            deliver(accepted).andExpect(status().isNoContent()); // Payload retained by an old worker is still harmless.
            deliver(retraction).andExpect(status().isNoContent());
            assertThat(current(question)).isEqualTo(SupportQuestionStatus.UNANSWERED);
            assertThat(jdbc.sql("SELECT COUNT(*) FROM lifecycle_answer_outcomes WHERE question_id=:id").param("id",question.id()).query(Integer.class).single()).isZero();
            var later=event(question,12,"AI_ANSWERED"); deliver(later).andExpect(status().isNoContent());
            deliver(retraction).andExpect(status().isNoContent());
            assertThat(current(question)).isEqualTo(SupportQuestionStatus.AI_ANSWERED);
            assertThat(jdbc.sql("SELECT answer_id FROM lifecycle_answer_outcomes WHERE question_id=:id").param("id",question.id()).query(UUID.class).single())
                    .isEqualTo(mapper.readValue(later.payload(),AcceptedAnswerStatus.class).answerId());
        }
    }
    @Test void legacyUnboundStatusIsInvalidatedWithoutGuessingOrChangingHumanAndClosedOutcomes() throws Exception {
        for(var prior:List.of(SupportQuestionStatus.AI_ANSWERED,SupportQuestionStatus.AI_LOW_CONFIDENCE,SupportQuestionStatus.HUMAN_ANSWERED,
                SupportQuestionStatus.RESOLVED,SupportQuestionStatus.CANCELLED,SupportQuestionStatus.DUPLICATE)) {
            var question=question(); repository.updateStatus(question.id(),SupportQuestionStatus.UNANSWERED,prior);
            var change=new AcceptedAnswerStatus(UUID.randomUUID(),question.id(),question.channelId(),question.senderUserId(),"AI_ANSWERED");
            deliver(retraction(change,5)).andExpect(status().isNoContent());
            assertThat(current(question)).isEqualTo(prior==SupportQuestionStatus.AI_ANSWERED || prior==SupportQuestionStatus.AI_LOW_CONFIDENCE
                    ? SupportQuestionStatus.UNANSWERED : prior);
        }
    }
    @Test void laterAcceptanceDeliveredFirstCannotSuppressTheOlderAnswersRetractionReceipt() throws Exception {
        var question=question(); var old=event(question,1,"AI_ANSWERED");
        var oldAnswer=mapper.readValue(old.payload(),AcceptedAnswerStatus.class);
        var later=event(question,3,"AI_ANSWERED"); deliver(later).andExpect(status().isNoContent());
        deliver(retraction(oldAnswer,2)).andExpect(status().isNoContent());
        deliver(retraction(oldAnswer,2)).andExpect(status().isNoContent());
        deliver(old).andExpect(status().isNoContent());
        assertThat(current(question)).isEqualTo(SupportQuestionStatus.AI_ANSWERED);
        assertThat(jdbc.sql("SELECT answer_id FROM lifecycle_answer_outcomes WHERE question_id=:id").param("id",question.id()).query(UUID.class).single())
                .isEqualTo(mapper.readValue(later.payload(),AcceptedAnswerStatus.class).answerId());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM durable_outbox WHERE kind=:kind AND aggregate_key=:key")
                .param("kind",AnswerRetraction.NOTIFICATION_KIND).param("key",new AnswerRetraction(oldAnswer.answerId(),oldAnswer.questionId(),oldAnswer.channelId(),oldAnswer.authorId()).notificationKey())
                .query(Integer.class).single()).isEqualTo(1);
    }
    @Test void differentAnswerRetractionDoesNotChangeAnExactBoundOutcomeAndFailureRollsBack() throws Exception {
        var question=question(); var accepted=event(question,1,"AI_ANSWERED"); deliver(accepted).andExpect(status().isNoContent());
        var exact=mapper.readValue(accepted.payload(),AcceptedAnswerStatus.class);
        var other=new AcceptedAnswerStatus(UUID.randomUUID(),question.id(),question.channelId(),question.senderUserId(),"AI_ANSWERED");
        deliver(retraction(other,2)).andExpect(status().isNoContent()); assertThat(current(question)).isEqualTo(SupportQuestionStatus.AI_ANSWERED);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("Synthetic retraction append failure"); })
                .when(outbox).append(eq("notification"),eq(AnswerRetraction.NOTIFICATION_KIND),anyString(),anyString());
        var event=retraction(exact,3);
        try { assertThatThrownBy(() -> deliver(event)).hasRootCauseInstanceOf(IllegalStateException.class); }
        finally { reset(outbox); }
        assertThat(current(question)).isEqualTo(SupportQuestionStatus.AI_ANSWERED);
        deliver(event).andExpect(status().isNoContent()); assertThat(current(question)).isEqualTo(SupportQuestionStatus.UNANSWERED);
    }

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
    private DurableEvent retraction(AcceptedAnswerStatus change,long revision) throws Exception {
        var payload=new AnswerRetraction(change.answerId(),change.questionId(),change.channelId(),change.authorId());
        return new DurableEvent(UUID.randomUUID(),1,"agent",revision,AnswerRetraction.KIND,payload.aggregateKey(),mapper.writeValueAsString(payload));
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
        return jdbc.sql("SELECT COUNT(*) FROM durable_outbox WHERE destination='notification' AND kind='NOTIFICATION' AND payload LIKE :key")
                .param("key", "%questionId=" + question.id() + "%").query(Integer.class).single();
    }
}
