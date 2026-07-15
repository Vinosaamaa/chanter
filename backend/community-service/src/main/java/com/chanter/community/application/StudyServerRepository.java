package com.chanter.community.application;

import com.chanter.community.domain.CoMember;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerInvitation;
import com.chanter.community.domain.VoicePresence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyServerRepository {

    StudyServer save(StudyServer studyServer);

    Optional<StudyServer> findById(UUID id);

    Optional<StudyServerChannel> findChannelById(UUID channelId);

    boolean isStudyServerMember(UUID studyServerId, UUID userId);

    boolean shareStudyServerMembership(UUID firstUserId, UUID secondUserId);

    List<CoMember> findCoMembers(UUID viewerUserId);

    VoicePresence saveVoicePresence(UUID channelId, UUID memberUserId);

    List<VoicePresence> findVoicePresences(UUID channelId);

    void deleteVoicePresence(UUID channelId, UUID memberUserId);

    Optional<UUID> findDefaultVoiceChannelId(UUID studyServerId);

    void deleteById(UUID id);

    StudyServerInvitation saveInvitation(StudyServerInvitation invitation);

    List<StudyServerInvitation> findPendingInvitations(UUID studyServerId);

    List<StudyServerInvitation> findPendingInvitationsForUser(UUID invitedUserId);

    Optional<StudyServerInvitation> findInvitation(UUID studyServerId, UUID invitationId);

    void acceptInvitation(UUID studyServerId, UUID invitationId, UUID acceptedByUserId, Instant resolvedAt);
}
