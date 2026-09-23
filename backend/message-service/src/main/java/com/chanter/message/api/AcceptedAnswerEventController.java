package com.chanter.message.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.AcceptedAnswerStatus;
import com.chanter.common.events.AnswerRetraction;
import com.chanter.common.events.DurableOutbox;
import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.InternalEventAccess;
import com.chanter.message.application.SupportQuestionService;
import com.chanter.message.domain.SupportQuestionStatus;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AcceptedAnswerEventController {
    private final DurableConsumer consumer;
    private final InternalEventAccess access;
    private final SupportQuestionService questions;
    private final ObjectReader payloadReader;
    private final ObjectReader retractionReader;
    private final JdbcTemplate jdbc;
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final com.chanter.message.lifecycle.MessageLifecycleAccess lifecycle;
    public AcceptedAnswerEventController(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            SupportQuestionService questions, ObjectMapper mapper,DurableOutbox outbox, @Value("${chanter.internal-service-token}") String token) {
        consumer = new DurableConsumer(jdbc, new TransactionTemplate(transactions));
        access = new InternalEventAccess(token);
        this.questions = questions;
        this.jdbc=jdbc; this.outbox=outbox; this.mapper=mapper;
        lifecycle=new com.chanter.message.lifecycle.MessageLifecycleAccess(org.springframework.jdbc.core.simple.JdbcClient.create(jdbc));
        payloadReader = mapper.readerFor(AcceptedAnswerStatus.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        retractionReader=mapper.readerFor(AnswerRetraction.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    @PostMapping("/api/v1/internal/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestBody DurableEvent event,
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String token) {
        access.require(token);
        try {
            event.validate();
            if (!"agent".equals(event.producer())) throw new IllegalArgumentException();
            if(AnswerRetraction.KIND.equals(event.kind())) {
                AnswerRetraction retraction=retractionReader.readValue(event.payload());
                if(retraction==null || !retraction.aggregateKey().equals(event.aggregateKey())) throw new IllegalArgumentException();
                // A later answer may arrive first. It must not suppress this answer's notification removal/receipt.
                var removal=new DurableEvent(event.id(),event.schemaVersion(),event.producer(),event.revision(),event.kind(),
                        "ANSWER_RETRACTED:"+retraction.answerId(),event.payload());
                consumer.apply(removal,true,() -> {
                    consumer.apply(event,false,() -> { }); // Fence older acceptance without replacing a newer question cursor.
                    retract(retraction);
                });
                return;
            }
            if(!AcceptedAnswerStatus.KIND.equals(event.kind())) throw new IllegalArgumentException();
            AcceptedAnswerStatus change = payloadReader.readValue(event.payload());
            if (change == null || !change.aggregateKey().equals(event.aggregateKey())) throw new IllegalArgumentException();
            consumer.apply(event, false, () -> {
                lifecycle.lock();
                if(lifecycle.accountDeleted(change.authorId()) || lifecycle.scopeDeleted("CHANNEL",change.channelId())) return;
                var before=jdbc.query("SELECT status FROM support_questions WHERE id=? AND channel_id=? AND sender_user_id=?",
                        (rs,n) -> rs.getString(1),change.questionId(),change.channelId(),change.authorId());
                var outcome=questions.reconcileAcceptedAnswerStatus(change.channelId(), change.questionId(),
                        change.authorId(), SupportQuestionStatus.valueOf(change.status()),change.answerId());
                if(before.size()==1 && "UNANSWERED".equals(before.getFirst()) && outcome.status().name().equals(change.status())) {
                    jdbc.update("DELETE FROM lifecycle_answer_outcomes WHERE question_id=?",change.questionId());
                    jdbc.update("INSERT INTO lifecycle_answer_outcomes(question_id,answer_id,channel_id,author_id,status) VALUES (?,?,?,?,?)",
                            change.questionId(),change.answerId(),change.channelId(),change.authorId(),change.status());
                }
            });
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid accepted answer event");
        }
    }
    private void retract(AnswerRetraction change) {
        lifecycle.lock();
        var statuses=jdbc.query("SELECT status FROM lifecycle_answer_outcomes WHERE question_id=? AND answer_id=? AND channel_id=? AND author_id=?",
                (rs,n) -> rs.getString(1),change.questionId(),change.answerId(),change.channelId(),change.authorId());
        if(statuses.size()==1) {
            jdbc.update("UPDATE support_questions SET status='UNANSWERED' WHERE id=? AND channel_id=? AND sender_user_id=? AND status=?",
                    change.questionId(),change.channelId(),change.authorId(),statuses.getFirst());
            jdbc.update("DELETE FROM lifecycle_answer_outcomes WHERE question_id=? AND answer_id=?",change.questionId(),change.answerId());
        }
        // Legacy derived status has no provable answer binding. Invalidate it conservatively; never relabel it as this answer.
        if(statuses.isEmpty()) jdbc.update("""
                UPDATE support_questions SET status='UNANSWERED' WHERE id=? AND channel_id=? AND sender_user_id=?
                    AND status IN ('AI_ANSWERED','AI_LOW_CONFIDENCE')
                    AND NOT EXISTS(SELECT 1 FROM lifecycle_answer_outcomes a WHERE a.question_id=support_questions.id)
                """,change.questionId(),change.channelId(),change.authorId());
        var receipt=new com.chanter.common.events.AnswerReconciliation(change,"COMPLETE");
        try { outbox.append("notification",AnswerRetraction.NOTIFICATION_KIND,change.notificationKey(),mapper.writeValueAsString(receipt)); }
        catch(JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid answer notification retraction",invalid); }
    }
    /** Notification visibility uses the same current question access check and an exact saved-answer binding. */
    @GetMapping("/api/v1/course-channels/{channelId}/accepted-answers/{answerId}")
    public AnswerVisibility visible(@PathVariable java.util.UUID channelId,@PathVariable java.util.UUID answerId,
            @RequestAttribute(com.chanter.common.auth.AuthRequestAttributes.USER_ID) java.util.UUID user) {
        var rows=jdbc.query("SELECT question_id FROM lifecycle_answer_outcomes WHERE answer_id=? AND channel_id=?",
                (rs,n) -> rs.getObject(1,java.util.UUID.class),answerId,channelId);
        if(rows.size()!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Answer outcome not found");
        var question=questions.getSupportQuestion(channelId,rows.getFirst(),user);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_answer_outcomes WHERE answer_id=? AND channel_id=?",Integer.class,answerId,channelId)!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Answer outcome not found");
        return new AnswerVisibility(answerId,question.status().name());
    }
    public record AnswerVisibility(java.util.UUID id,String status) { }
}
