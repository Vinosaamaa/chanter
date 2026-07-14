package com.chanter.community.domain;

import java.util.UUID;

public record CoMember(
        UUID userId,
        String sharedStudyServerName
) {
}
