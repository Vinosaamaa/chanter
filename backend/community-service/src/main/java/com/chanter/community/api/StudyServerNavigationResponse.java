package com.chanter.community.api;

import com.chanter.community.domain.StudyServerNavigation;
import java.util.UUID;

public record StudyServerNavigationResponse(
        UUID studyServerId,
        String studyServerName,
        boolean canViewFullCatalog,
        StudyServerCapabilitiesResponse capabilities,
        java.util.List<StudyAssistantGrantCandidatesResponse.ChannelResponse> studyServerChannels,
        java.util.List<CourseResponse> courses
) {

    static StudyServerNavigationResponse from(StudyServerNavigation navigation) {
        return new StudyServerNavigationResponse(
                navigation.studyServerId(),
                navigation.studyServerName(),
                navigation.canViewFullCatalog(),
                StudyServerCapabilitiesResponse.from(navigation.capabilities()),
                navigation.studyServerChannels().stream()
                        .map(StudyAssistantGrantCandidatesResponse.ChannelResponse::fromStudyServerChannel)
                        .toList(),
                navigation.courses().stream()
                        .map(CourseResponse::from)
                        .toList()
        );
    }

    public record CourseResponse(
            UUID id,
            String title,
            CourseCapabilitiesResponse capabilities,
            java.util.List<CohortResponse> cohorts,
            java.util.List<StudyAssistantGrantCandidatesResponse.ChannelResponse> channels
    ) {

        static CourseResponse from(com.chanter.community.domain.StudyServerNavigationCourse course) {
            return new CourseResponse(
                    course.id(),
                    course.title(),
                    CourseCapabilitiesResponse.from(course.capabilities()),
                    course.cohorts().stream().map(CohortResponse::from).toList(),
                    course.channels().stream()
                            .map(StudyAssistantGrantCandidatesResponse.ChannelResponse::fromCourseChannel)
                            .toList()
            );
        }
    }

    public record CourseCapabilitiesResponse(
            boolean instructor,
            boolean teachingAssistant,
            boolean enrolled,
            boolean canManageCourse,
            boolean canManageQuestions,
            boolean canApproveFaq,
            boolean canManageTaQueue,
            boolean canUploadResources,
            boolean canScheduleOfficeHours,
            boolean canManagePeople
    ) {

        static CourseCapabilitiesResponse from(com.chanter.community.domain.CourseCapabilities capabilities) {
            return new CourseCapabilitiesResponse(
                    capabilities.instructor(),
                    capabilities.teachingAssistant(),
                    capabilities.enrolled(),
                    capabilities.canManageCourse(),
                    capabilities.canManageQuestions(),
                    capabilities.canApproveFaq(),
                    capabilities.canManageTaQueue(),
                    capabilities.canUploadResources(),
                    capabilities.canScheduleOfficeHours(),
                    capabilities.canManagePeople()
            );
        }
    }

    public record CohortResponse(
            UUID id,
            String name,
            CohortCapabilitiesResponse capabilities
    ) {

        static CohortResponse from(com.chanter.community.domain.StudyServerNavigationCohort cohort) {
            return new CohortResponse(
                    cohort.id(),
                    cohort.name(),
                    CohortCapabilitiesResponse.from(cohort.capabilities())
            );
        }
    }

    public record CohortCapabilitiesResponse(
            boolean enrolled,
            boolean teachingAssistant,
            boolean canManage
    ) {

        static CohortCapabilitiesResponse from(com.chanter.community.domain.CohortCapabilities capabilities) {
            return new CohortCapabilitiesResponse(
                    capabilities.enrolled(),
                    capabilities.teachingAssistant(),
                    capabilities.canManage()
            );
        }
    }

    public record StudyServerCapabilitiesResponse(
            boolean owner,
            boolean canTeach,
            boolean canCreateCourse,
            boolean canManageCommunity,
            boolean canManageEvents,
            boolean canManageBilling
    ) {

        static StudyServerCapabilitiesResponse from(
                com.chanter.community.domain.StudyServerCapabilities capabilities
        ) {
            return new StudyServerCapabilitiesResponse(
                    capabilities.owner(),
                    capabilities.canTeach(),
                    capabilities.canCreateCourse(),
                    capabilities.canManageCommunity(),
                    capabilities.canManageEvents(),
                    capabilities.canManageBilling()
            );
        }
    }
}
