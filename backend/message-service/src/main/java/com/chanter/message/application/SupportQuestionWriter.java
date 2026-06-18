package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
class SupportQuestionWriter {

    private final SupportQuestionRepository repository;
    private final Clock clock;

    SupportQuestionWriter(SupportQuestionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    SupportQuestion createIfAbsent(
            UUID channelId,
            UUID senderUserId,
            String body,
            String idempotencyKey
    ) {
        return repository.findByChannelSenderAndIdempotencyKey(channelId, senderUserId, idempotencyKey)
                .orElseGet(() -> {
                    SupportQuestion supportQuestion = new SupportQuestion(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            channelId,
                            senderUserId,
                            body,
                            SupportQuestionStatus.UNANSWERED,
                            idempotencyKey,
                            clock.instant()
                    );

                    try {
                        return repository.saveSupportQuestion(supportQuestion);
                    } catch (DataIntegrityViolationException exception) {
                        return repository.findByChannelSenderAndIdempotencyKey(channelId, senderUserId, idempotencyKey)
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Support Question idempotency key is already in use with different content"
                                ));
                    }
                });
    }
}
