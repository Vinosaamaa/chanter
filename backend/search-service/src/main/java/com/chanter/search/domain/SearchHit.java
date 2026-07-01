package com.chanter.search.domain;

import java.util.UUID;

public record SearchHit(
        SearchDocumentType documentType,
        UUID courseId,
        String courseTitle,
        UUID sourceId,
        String title,
        String snippet
) {
}
