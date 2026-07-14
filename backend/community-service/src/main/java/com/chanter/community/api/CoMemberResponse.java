package com.chanter.community.api;

import com.chanter.community.domain.CoMember;
import java.util.UUID;

public record CoMemberResponse(
        UUID userId,
        String sharedStudyServerName
) {

    public static CoMemberResponse from(CoMember coMember) {
        return new CoMemberResponse(coMember.userId(), coMember.sharedStudyServerName());
    }
}
