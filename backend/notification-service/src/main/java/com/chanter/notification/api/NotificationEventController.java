package com.chanter.notification.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.InternalEventAccess;
import com.chanter.common.events.AnswerRetraction;
import com.chanter.common.events.DurableOutbox;
import com.chanter.notification.application.NotificationRepository;
import com.chanter.notification.application.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/events")
public class NotificationEventController {
    private final DurableConsumer consumer;
    private final InternalEventAccess access;
    private final NotificationService notifications;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private final DurableOutbox outbox;
    private final com.fasterxml.jackson.databind.ObjectReader retractions;
    public NotificationEventController(JdbcTemplate jdbc, PlatformTransactionManager transactions, NotificationService notifications,
            ObjectMapper mapper, Validator validator,DurableOutbox outbox, @Value("${chanter.internal-service-token}") String token) {
        this.consumer = new DurableConsumer(jdbc, new TransactionTemplate(transactions));
        this.access = new InternalEventAccess(token);
        this.notifications = notifications;
        this.mapper = mapper;
        this.validator = validator;
        this.jdbc=jdbc; this.outbox=outbox;
        retractions=mapper.readerFor(com.chanter.common.events.AnswerReconciliation.class).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestBody DurableEvent event, @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        try {
            event.validate();
            if(AnswerRetraction.NOTIFICATION_KIND.equals(event.kind())) {
                com.chanter.common.events.AnswerReconciliation receipt=retractions.readValue(event.payload());
                if(receipt==null) throw new IllegalArgumentException();
                AnswerRetraction change=receipt.answer();
                if(!"message".equals(event.producer()) || !change.notificationKey().equals(event.aggregateKey())) throw new IllegalArgumentException();
                consumer.apply(event,false,() -> {
                    jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_retracted_answers WHERE answer_id=?",Integer.class,change.answerId())==0)
                        jdbc.update("INSERT INTO lifecycle_retracted_answers(answer_id) VALUES (?)",change.answerId());
                    jdbc.update("DELETE FROM notifications WHERE user_id=? AND source_type=? AND source_id=? AND kind='SUPPORT_QUESTION_ANSWERED'",
                            change.authorId(),AnswerRetraction.SOURCE_TYPE,change.answerId());
                    try { outbox.append("agent",AnswerRetraction.RECEIPT_KIND,change.receiptKey(),mapper.writeValueAsString(receipt)); }
                    catch(JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid answer reconciliation receipt",invalid); }
                });
                return;
            }
            if (!event.kind().equals("NOTIFICATION") || !java.util.Set.of("community", "message").contains(event.producer())) throw new IllegalArgumentException();
            var request = mapper.readValue(event.payload(), CreateNotificationRequest.class);
            if (!validator.validate(request).isEmpty()) throw new IllegalArgumentException();
            String key = com.chanter.common.events.NotificationEventWriter.aggregateKey(
                    request.userId(), request.sourceType(), request.sourceId(), request.kind().name());
            if (!key.equals(event.aggregateKey())) throw new IllegalArgumentException();
            consumer.apply(event, false, () -> notifications.create(new NotificationRepository.CreateCommand(request.userId(), request.kind(),
                    request.filterBucket(), request.title(), request.bodyPreview(), request.courseLabel(), request.href(), request.sourceType(),
                    request.sourceId(), request.studyServerId(), request.courseId(), request.cohortId(), request.channelId())));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification event");
        }
    }
}
