package com.chanter.message.application;

import com.chanter.message.domain.ChannelMessage;
import com.chanter.message.domain.ChannelScope;
import com.chanter.common.auth.ModerationAccess;
import com.chanter.common.auth.ModerationAccess.Target;
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
    private final ModerationAccess moderation;

    public ChannelMessageService(
            ChannelMessageRepository repository,
            ChannelMessageAccessClient accessClient,
            Clock clock,
            com.chanter.common.events.SearchEventWriter searchEvents,
            ModerationAccess moderation
    ) {
        this.repository = repository;
        this.accessClient = accessClient;
        this.clock = clock;
        this.searchEvents = searchEvents;
        this.moderation = moderation;
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

        requireServer(viewerUserId, access);
        var messages = repository.listByChannelSince(channelId, since, afterMessageId, MAX_PAGE_SIZE);
        var visible = new java.util.ArrayList<ChannelMessage>();
        for (int start=0; start<messages.size(); start+=100) {
            var page = messages.subList(start, Math.min(start+100,messages.size()));
            var allowed = moderation.allowedSources(viewerUserId,page.stream().map(message -> new Target("MESSAGE",message.id())).toList());
            page.stream().filter(message -> allowed.contains(new Target("MESSAGE",message.id()))).forEach(visible::add);
        }
        return List.copyOf(visible);
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
        requireServer(senderUserId, access);

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
        var access = accessClient.requireAccess(channelId, viewer, scope);
        if (!access.canReadMessages()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel read access denied");
        }
        requireServer(viewer, access);
        var message = repository.findByIdAndChannelId(messageId, channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        moderation.requireAllowed(viewer,List.of(new Target("MESSAGE",messageId)));
        return message;
    }

    private void requireServer(UUID viewer, ChannelMessageAccess access) {
        if (access.studyServerId() == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Current channel scope is unavailable");
        moderation.requireAllowed(viewer,List.of(new Target("STUDY_SERVER",access.studyServerId())));
    }
}
