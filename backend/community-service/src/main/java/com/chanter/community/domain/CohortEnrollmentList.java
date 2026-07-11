package com.chanter.community.domain;

import java.util.List;

public record CohortEnrollmentList(List<CohortEnrollment> enrollments, int totalCount) {
}
