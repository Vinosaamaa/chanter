package com.chanter.community.api;

import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerInvitation;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record StudyServerResponse(
        UUID id,
        String name,
        String description,
        String serverType,
        OwnerRoleResponse ownerRole,
        String planTier,
        List<ChannelResponse> channels,
        List<PendingInvitationResponse> pendingInvitations
) {

    static StudyServerResponse from(StudyServer studyServer) {
        return from(studyServer, List.of());
    }

    static StudyServerResponse from(StudyServer studyServer, List<StudyServerInvitation> pendingInvitations) {
        return new StudyServerResponse(
                studyServer.id(),
                studyServer.name(),
                studyServer.description(),
                studyServer.serverType() == null ? null : studyServer.serverType().toApiValue(),
                new OwnerRoleResponse(
                        studyServer.ownerRole().userId(),
                        studyServer.ownerRole().role().name()
                ),
                studyServer.planTier().name(),
                studyServer.channels().stream()
                        .sorted(Comparator.comparingInt(channel -> channel.position()))
                        .map(channel -> new ChannelResponse(
                                channel.id(),
                                channel.name(),
                                channel.kind().name()
                        ))
                        .toList(),
                pendingInvitations.stream()
                        .map(PendingInvitationResponse::from)
                        .toList()
        );
    }

    public record OwnerRoleResponse(UUID userId, String role) {
    }

    public record ChannelResponse(UUID id, String name, String kind) {
    }

    public record PendingInvitationResponse(UUID id, String email) {
        static PendingInvitationResponse from(StudyServerInvitation invitation) {
            return new PendingInvitationResponse(invitation.id(), invitation.email());
        }
    }
}
