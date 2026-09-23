package com.chanter.media.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.*;
import com.chanter.media.application.ResourceLifecycle;
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
public class ResourceDeletionEventController {
    private final InternalEventAccess access;
    private final DurableConsumer consumer;
    private final ResourceLifecycle resources;
    private final com.fasterxml.jackson.databind.ObjectReader reader;
    public ResourceDeletionEventController(JdbcTemplate jdbc,PlatformTransactionManager transactions,ResourceLifecycle resources,
            ObjectMapper mapper,@Value("${chanter.internal-service-token}") String token) {
        access=new InternalEventAccess(token);var tx=new TransactionTemplate(transactions);tx.setTimeout(30);
        consumer=new DurableConsumer(jdbc,tx);this.resources=resources;
        reader=mapper.readerFor(ResourceDeletionReceipt.class).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@RequestBody DurableEvent event,@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String token) {
        access.require(token);
        try {
            event.validate();ResourceDeletionReceipt receipt=reader.readValue(event.payload());
            if(receipt==null || !"agent".equals(event.producer()) || !ResourceDeletionReceipt.KIND.equals(event.kind())
                    || !receipt.key().equals(event.aggregateKey())) throw new IllegalArgumentException("Invalid deletion receipt");
            consumer.apply(event,false,() -> resources.acknowledgeDeletion(receipt));
        } catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid resource deletion receipt");
        }
    }
}
