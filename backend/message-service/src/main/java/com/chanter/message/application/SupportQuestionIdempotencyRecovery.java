package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
class SupportQuestionIdempotencyRecovery {

    private final SupportQuestionRepository repository;

    SupportQuestionIdempotencyRecovery(SupportQuestionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    SupportQuestion requireExisting(
            UUID channelId,
            UUID senderUserId,
            String idempotencyKey
    ) {
        return repository.findByChannelSenderAndIdempotencyKey(channelId, senderUserId, idempotencyKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Support Question idempotency key is already in use with different content"
                ));
    }
}
