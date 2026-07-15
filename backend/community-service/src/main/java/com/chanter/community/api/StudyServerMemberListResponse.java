package com.chanter.community.api;

import com.chanter.community.application.StudyServerService;
import java.util.List;
import java.util.UUID;

public record StudyServerMemberListResponse(
        List<StudyServerMemberResponse> members,
        int filteredTotal,
        int memberCount
) {
    static StudyServerMemberListResponse from(StudyServerService.StudyServerMemberDirectory directory) {
        return new StudyServerMemberListResponse(
                directory.members().stream()
                        .map(StudyServerMemberResponse::from)
                        .toList(),
                directory.filteredTotal(),
                directory.memberCount()
        );
    }

    public record StudyServerMemberResponse(
            UUID userId,
            String displayName,
            String email,
            String role,
            boolean staff
    ) {
        static StudyServerMemberResponse from(StudyServerService.StudyServerMemberDetails member) {
            return new StudyServerMemberResponse(
                    member.userId(),
                    member.displayName(),
                    member.email(),
                    member.role(),
                    member.staff()
            );
        }
    }
}
