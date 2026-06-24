package com.chanter.community.application;

import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.VoicePresence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyServerRepository {

    StudyServer save(StudyServer studyServer);

    Optional<StudyServer> findById(UUID id);

    Optional<StudyServerChannel> findChannelById(UUID channelId);

    boolean isStudyServerMember(UUID studyServerId, UUID userId);

    VoicePresence saveVoicePresence(UUID channelId, UUID memberUserId);

    List<VoicePresence> findVoicePresences(UUID channelId);

    void deleteVoicePresence(UUID channelId, UUID memberUserId);

    Optional<UUID> findDefaultVoiceChannelId(UUID studyServerId);
}
