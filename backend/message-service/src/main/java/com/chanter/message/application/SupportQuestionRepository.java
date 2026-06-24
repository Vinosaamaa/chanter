package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportQuestionRepository {

    SupportQuestion saveSupportQuestion(SupportQuestion supportQuestion);

    Optional<SupportQuestion> findByChannelSenderAndIdempotencyKey(
            UUID channelId,
            UUID senderUserId,
            String idempotencyKey
    );

    List<SupportQuestion> findByChannelId(UUID channelId);

    List<SupportQuestion> findByChannelIdAndStatus(UUID channelId, SupportQuestionStatus status);

    Optional<SupportQuestion> findByIdAndChannelId(UUID channelId, UUID supportQuestionId);

    boolean updateStatus(UUID supportQuestionId, SupportQuestionStatus fromStatus, SupportQuestionStatus toStatus);
}
