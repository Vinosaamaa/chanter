package com.chanter.search.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.*;
import com.chanter.search.infra.JdbcSearchIndexRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/events")
public class SearchEventController {
    private final DurableConsumer consumer;
    private final InternalEventAccess access;
    private final JdbcSearchIndexRepository index;
    private final ObjectMapper mapper;
    public SearchEventController(JdbcTemplate jdbc, PlatformTransactionManager transactions, JdbcSearchIndexRepository index,
            ObjectMapper mapper, @Value("${chanter.internal-service-token}") String token) {
        this.consumer = new DurableConsumer(jdbc, new TransactionTemplate(transactions));
        this.access = new InternalEventAccess(token);
        this.index = index;
        this.mapper = mapper;
    }
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestBody DurableEvent event, @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        try {
            event.validate();
            var change = mapper.readValue(event.payload(), SearchChange.class);
            if (change == null || change.type() == null) throw new IllegalArgumentException();
            String producer = switch (change.type()) {
                case "EVENT", "ANNOUNCEMENT" -> "community";
                case "FAQ", "MESSAGE" -> "message";
                case "RESOURCE" -> "media";
                default -> throw new IllegalArgumentException();
            };
            if (!producer.equals(event.producer()) || !change.type().equals(event.kind()) || change.sourceId() == null
                    || !event.aggregateKey().equals(change.type() + ":" + change.sourceId())
                    || (change.studyServerId() == null && change.courseId() == null)
                    || java.util.Set.of("EVENT", "ANNOUNCEMENT").contains(change.type()) && change.studyServerId() == null
                    || java.util.Set.of("RESOURCE", "FAQ").contains(change.type()) && change.courseId() == null
                    || change.type().equals("MESSAGE") && (change.channelId() == null || change.channelScope() == null
                        || !java.util.Set.of("COURSE", "STUDY_SERVER").contains(change.channelScope())
                        || change.channelScope().equals("COURSE") && change.courseId() == null
                        || change.channelScope().equals("STUDY_SERVER") && change.studyServerId() == null)
                    || change.href() != null && (!change.href().startsWith("/app/") || change.href().length() > 1024)) throw new IllegalArgumentException();
            consumer.apply(event, change.deleted(), () -> index.apply(change));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search event");
        }
    }
}
