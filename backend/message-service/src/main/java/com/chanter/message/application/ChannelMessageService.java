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

    public ChannelMessageService(
            ChannelMessageRepository repository,
            ChannelMessageAccessClient accessClient,
            Clock clock
    ) {
        this.repository = repository;
        this.accessClient = accessClient;
        this.clock = clock;
    }

    public List<ChannelMessage> listMessages(
            UUID channelId,
            UUID viewerUserId,
            ChannelScope channelScope,
            Optional<Instant> since
    ) {
        ChannelMessageAccess access = accessClient.requireAccess(channelId, viewerUserId, channelScope);
        if (!access.canReadMessages()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel read access denied");
        }

        return repository.listByChannelSince(channelId, since, MAX_PAGE_SIZE);
    }

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

        return repository.save(new ChannelMessage(
                UUID.randomUUID(),
                channelId,
                senderUserId,
                trimmedBody,
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }
}
