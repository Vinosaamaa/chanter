package com.chanter.community.api;

import com.chanter.community.application.StudyServerService;
import java.util.List;
import java.util.UUID;

public record StudyServerMemberSummaryResponse(
        int memberCount,
        List<MemberPreviewResponse> preview
) {
    static StudyServerMemberSummaryResponse from(StudyServerService.StudyServerMemberSummary summary) {
        return new StudyServerMemberSummaryResponse(
                summary.memberCount(),
                summary.preview().stream()
                        .map(preview -> new MemberPreviewResponse(preview.userId(), preview.displayName()))
                        .toList()
        );
    }

    public record MemberPreviewResponse(UUID userId, String displayName) {
    }
}
