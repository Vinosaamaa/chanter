package com.chanter.community.application;

import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.OwnerRole;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerRole;
import com.chanter.community.domain.VoicePresence;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyServerService {

    private final StudyServerRepository repository;
    private final Clock clock;

    public StudyServerService(StudyServerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public StudyServer createStudyServer(String name, UUID ownerUserId) {
        UUID studyServerId = UUID.randomUUID();
        StudyServer studyServer = new StudyServer(
                studyServerId,
                name.trim(),
                new OwnerRole(ownerUserId, StudyServerRole.STUDY_SERVER_OWNER),
                List.of(
                        new StudyServerChannel(UUID.randomUUID(), studyServerId, "announcements", ChannelKind.TEXT, 0),
                        new StudyServerChannel(UUID.randomUUID(), studyServerId, "general", ChannelKind.TEXT, 1),
                        new StudyServerChannel(UUID.randomUUID(), studyServerId, "study-room", ChannelKind.VOICE, 2)
                ),
                clock.instant()
        );

        return repository.save(studyServer);
    }

    public Optional<StudyServer> findStudyServer(UUID id) {
        return repository.findById(id);
    }

    public VoicePresence joinVoiceChannel(UUID channelId, UUID memberUserId) {
        StudyServerChannel channel = requireVoiceChannel(channelId);
        requireStudyServerMember(channel.studyServerId(), memberUserId);

        return repository.saveVoicePresence(channelId, memberUserId);
    }

    public List<VoicePresence> findVoicePresences(UUID channelId, UUID viewerUserId) {
        StudyServerChannel channel = requireVoiceChannel(channelId);
        requireStudyServerMember(channel.studyServerId(), viewerUserId);

        return repository.findVoicePresences(channelId);
    }

    public void leaveVoiceChannel(UUID channelId, UUID actingUserId, UUID memberUserId) {
        // Interim MVP: caller identity comes from request params until Auth Service supplies a principal.
        if (!actingUserId.equals(memberUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Members can only remove their own voice presence"
            );
        }

        StudyServerChannel channel = requireVoiceChannel(channelId);
        requireStudyServerMember(channel.studyServerId(), actingUserId);

        repository.deleteVoicePresence(channelId, memberUserId);
    }

    private StudyServerChannel requireVoiceChannel(UUID channelId) {
        StudyServerChannel channel = repository.findChannelById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server Channel not found"));
        if (channel.kind() != ChannelKind.VOICE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study Server Channel is not a Voice Channel");
        }
        return channel;
    }

    private void requireStudyServerMember(UUID studyServerId, UUID userId) {
        if (!repository.isStudyServerMember(studyServerId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Voice Channel access requires Study Server membership");
        }
    }
}
