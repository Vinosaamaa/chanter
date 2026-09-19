package com.chanter.message.application;

import com.chanter.message.domain.ChannelMessage;
import com.chanter.message.domain.ChannelScope;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelMessageService {

    private static final int MAX_PAGE_SIZE = 200;

    private final ChannelMessageRepository repository;
    private final ChannelMessageAccessClient accessClient;
    private final Clock clock;
    private final com.chanter.common.events.SearchEventWriter searchEvents;

    public ChannelMessageService(
            ChannelMessageRepository repository,
            ChannelMessageAccessClient accessClient,
            Clock clock,
            com.chanter.common.events.SearchEventWriter searchEvents
    ) {
        this.repository = repository;
        this.accessClient = accessClient;
        this.clock = clock;
        this.searchEvents = searchEvents;
    }

    public List<ChannelMessage> listMessages(
            UUID channelId,
            UUID viewerUserId,
            ChannelScope channelScope,
            Optional<Instant> since,
            Optional<UUID> afterMessageId
    ) {
        if (afterMessageId.isPresent() && since.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "afterMessageId requires since");
        }

        ChannelMessageAccess access = accessClient.requireAccess(channelId, viewerUserId, channelScope);
        if (!access.canReadMessages()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel read access denied");
        }

        return repository.listByChannelSince(channelId, since, afterMessageId, MAX_PAGE_SIZE);
    }

    @org.springframework.transaction.annotation.Transactional
    public ChannelMessage postMessage(
            UUID channelId,
            UUID senderUserId,
            ChannelScope channelScope,
            String body
    ) {
        String trimmedBody = body == null ? "" : body.trim();
        if (trimmedBody.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message body must not be blank");
        }

        ChannelMessageAccess access = accessClient.requireAccess(channelId, senderUserId, channelScope);
        if (!access.canPostMessages()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel post access denied");
        }

        ChannelMessage saved = repository.save(new ChannelMessage(
                UUID.randomUUID(),
                channelId,
                senderUserId,
                trimmedBody,
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
        searchEvents.append(new com.chanter.common.events.SearchChange("MESSAGE", saved.id(), access.studyServerId(),
                access.courseId(), null, channelId, channelScope.name(), "Channel message", saved.body(),
                null, false));
        return saved;
    }

    public ChannelMessage getMessage(UUID channelId, UUID messageId, UUID viewer, ChannelScope scope) {
        if (!accessClient.requireAccess(channelId, viewer, scope).canReadMessages()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel read access denied");
        }
        return repository.findByIdAndChannelId(messageId, channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
    }
}
