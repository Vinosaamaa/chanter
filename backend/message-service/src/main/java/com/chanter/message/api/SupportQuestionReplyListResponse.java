package com.chanter.message.api;

import java.util.List;

public record SupportQuestionReplyListResponse(
        List<SupportQuestionReplyResponse> replies
) {
}
