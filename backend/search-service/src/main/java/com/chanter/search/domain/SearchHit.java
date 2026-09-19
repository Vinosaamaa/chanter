package com.chanter.search.domain;

import java.util.UUID;

public record SearchHit(
        SearchDocumentType documentType,
        UUID courseId,
        String courseTitle,
        UUID sourceId,
        String title,
        String snippet,
        String href,
        UUID channelId,
        String channelScope
) {
    public SearchHit(SearchDocumentType type, UUID courseId, String courseTitle, UUID sourceId, String title, String snippet) {
        this(type, courseId, courseTitle, sourceId, title, snippet, null, null, null);
    }
}
