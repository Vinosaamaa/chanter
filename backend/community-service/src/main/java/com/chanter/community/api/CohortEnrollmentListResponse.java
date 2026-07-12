package com.chanter.community.api;

import java.util.List;

public record CohortEnrollmentListResponse(
        List<CohortEnrollmentResponse> enrollments,
        int totalCount,
        int limit,
        int offset
) {
}
