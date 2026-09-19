package com.chanter.notification.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.InternalEventAccess;
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
    public NotificationEventController(JdbcTemplate jdbc, PlatformTransactionManager transactions, NotificationService notifications,
            ObjectMapper mapper, Validator validator, @Value("${chanter.internal-service-token}") String token) {
        this.consumer = new DurableConsumer(jdbc, new TransactionTemplate(transactions));
        this.access = new InternalEventAccess(token);
        this.notifications = notifications;
        this.mapper = mapper;
        this.validator = validator;
    }
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestBody DurableEvent event, @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        try {
            event.validate();
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
