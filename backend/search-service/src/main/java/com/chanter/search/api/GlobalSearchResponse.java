package com.chanter.search.api;

import com.chanter.search.domain.SearchDocumentType;
import com.chanter.search.domain.SearchHit;
import java.util.List;
import java.util.UUID;

public record GlobalSearchResponse(List<SearchHitResponse> results) {

    static GlobalSearchResponse from(List<SearchHit> hits) {
        return new GlobalSearchResponse(hits.stream().map(SearchHitResponse::from).toList());
    }

    public record SearchHitResponse(
            SearchDocumentType documentType,
            UUID courseId,
            String courseTitle,
            UUID sourceId,
            String title,
            String snippet
    ) {
        static SearchHitResponse from(SearchHit hit) {
            return new SearchHitResponse(
                    hit.documentType(),
                    hit.courseId(),
                    hit.courseTitle(),
                    hit.sourceId(),
                    hit.title(),
                    hit.snippet()
            );
        }
    }
}
