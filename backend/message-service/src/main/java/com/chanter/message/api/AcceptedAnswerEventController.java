package com.chanter.message.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.AcceptedAnswerStatus;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AcceptedAnswerEventController {
    private final DurableConsumer consumer;
    private final InternalEventAccess access;
    private final SupportQuestionService questions;
    private final ObjectReader payloadReader;
    private final com.chanter.message.lifecycle.MessageLifecycleAccess lifecycle;
    public AcceptedAnswerEventController(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            SupportQuestionService questions, ObjectMapper mapper, @Value("${chanter.internal-service-token}") String token) {
        consumer = new DurableConsumer(jdbc, new TransactionTemplate(transactions));
        access = new InternalEventAccess(token);
        this.questions = questions;
        lifecycle=new com.chanter.message.lifecycle.MessageLifecycleAccess(org.springframework.jdbc.core.simple.JdbcClient.create(jdbc));
        payloadReader = mapper.readerFor(AcceptedAnswerStatus.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    @PostMapping("/api/v1/internal/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestBody DurableEvent event,
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String token) {
        access.require(token);
        try {
            event.validate();
            if (!"agent".equals(event.producer()) || !AcceptedAnswerStatus.KIND.equals(event.kind())) throw new IllegalArgumentException();
            AcceptedAnswerStatus change = payloadReader.readValue(event.payload());
            if (change == null || !change.aggregateKey().equals(event.aggregateKey())) throw new IllegalArgumentException();
            consumer.apply(event, false, () -> {
                lifecycle.lock();
                if(lifecycle.accountDeleted(change.authorId()) || lifecycle.scopeDeleted("CHANNEL",change.channelId())) return;
                questions.reconcileAcceptedAnswerStatus(change.channelId(), change.questionId(),
                        change.authorId(), SupportQuestionStatus.valueOf(change.status()));
            });
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid accepted answer event");
        }
    }
}
