package com.chanter.community.api;

import com.chanter.community.domain.StudyServer;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record StudyServerResponse(
        UUID id,
        String name,
        OwnerRoleResponse ownerRole,
        List<ChannelResponse> channels
) {

    static StudyServerResponse from(StudyServer studyServer) {
        return new StudyServerResponse(
                studyServer.id(),
                studyServer.name(),
                new OwnerRoleResponse(
                        studyServer.ownerRole().userId(),
                        studyServer.ownerRole().role().name()
                ),
                studyServer.channels().stream()
                        .sorted(Comparator.comparingInt(channel -> channel.position()))
                        .map(channel -> new ChannelResponse(
                                channel.id(),
                                channel.name(),
                                channel.kind().name()
                        ))
                        .toList()
        );
    }

    public record OwnerRoleResponse(UUID userId, String role) {
    }

    public record ChannelResponse(UUID id, String name, String kind) {
    }
}
