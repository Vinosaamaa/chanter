package com.chanter.community.api;

import com.chanter.community.domain.CoMember;
import java.util.List;

public record CoMemberListResponse(List<CoMemberResponse> coMembers) {

    public static CoMemberListResponse from(List<CoMember> coMembers) {
        return new CoMemberListResponse(coMembers.stream().map(CoMemberResponse::from).toList());
    }
}
