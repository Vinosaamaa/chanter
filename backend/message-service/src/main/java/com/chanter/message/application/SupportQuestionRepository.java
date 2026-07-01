package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SupportQuestionRepository {

    SupportQuestion saveSupportQuestion(SupportQuestion supportQuestion);

    Optional<SupportQuestion> findByChannelSenderAndIdempotencyKey(
            UUID channelId,
            UUID senderUserId,
            String idempotencyKey
    );

    List<SupportQuestion> findByChannelId(UUID channelId);

    List<SupportQuestion> findByChannelIdAndCreatedAtBetween(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive
    );

    List<SupportQuestion> findByChannelIdAndStatus(UUID channelId, SupportQuestionStatus status);

    List<SupportQuestion> findByChannelIdAndSenderUserIdAndStatus(
            UUID channelId,
            UUID senderUserId,
            SupportQuestionStatus status
    );

    Optional<SupportQuestion> findByIdAndChannelId(UUID channelId, UUID supportQuestionId);

    Set<UUID> findIdsByChannelIdAndIds(UUID channelId, List<UUID> supportQuestionIds);

    boolean updateStatus(UUID supportQuestionId, SupportQuestionStatus fromStatus, SupportQuestionStatus toStatus);
}
