package com.chanter.community.api;

import com.chanter.community.domain.CourseCatalog;
import com.chanter.community.domain.CourseCatalogCohort;
import com.chanter.community.domain.CourseCatalogCourse;
import java.util.List;
import java.util.UUID;

public record CourseCatalogResponse(List<CourseResponse> courses) {

    static CourseCatalogResponse from(CourseCatalog catalog) {
        return new CourseCatalogResponse(catalog.courses().stream().map(CourseResponse::from).toList());
    }

    public record CourseResponse(
            UUID id,
            String title,
            UUID instructorUserId,
            List<CohortResponse> cohorts
    ) {
        static CourseResponse from(CourseCatalogCourse course) {
            return new CourseResponse(
                    course.id(),
                    course.title(),
                    course.instructorUserId(),
                    course.cohorts().stream().map(CohortResponse::from).toList()
            );
        }
    }

    public record CohortResponse(
            UUID id,
            String name,
            String enrollmentPolicy,
            boolean enrolled,
            int learnerCount
    ) {
        static CohortResponse from(CourseCatalogCohort cohort) {
            return new CohortResponse(
                    cohort.id(),
                    cohort.name(),
                    cohort.enrollmentPolicy().name(),
                    cohort.enrolled(),
                    cohort.learnerCount()
            );
        }
    }
}
