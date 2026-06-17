package com.chanter.community.application;

import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.OwnerRole;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerRole;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StudyServerService {

    private final StudyServerRepository repository;
    private final Clock clock;

    public StudyServerService(StudyServerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public StudyServer createStudyServer(String name, UUID ownerUserId) {
        StudyServer studyServer = new StudyServer(
                UUID.randomUUID(),
                name.trim(),
                new OwnerRole(ownerUserId, StudyServerRole.STUDY_SERVER_OWNER),
                List.of(
                        new StudyServerChannel(UUID.randomUUID(), "announcements", ChannelKind.TEXT, 0),
                        new StudyServerChannel(UUID.randomUUID(), "general", ChannelKind.TEXT, 1),
                        new StudyServerChannel(UUID.randomUUID(), "study-room", ChannelKind.VOICE, 2)
                ),
                clock.instant()
        );

        return repository.save(studyServer);
    }

    public Optional<StudyServer> findStudyServer(UUID id) {
        return repository.findById(id);
    }
}
