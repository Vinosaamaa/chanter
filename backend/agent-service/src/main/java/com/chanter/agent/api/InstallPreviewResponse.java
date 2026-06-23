package com.chanter.agent.api;

import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.ChannelCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CohortCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CourseCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.GrantCandidates;
import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.application.StudyAssistantService.InstallPreview;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import java.util.List;
import java.util.UUID;

public record InstallPreviewResponse(
        UUID studyServerId,
        boolean alreadyInstalled,
        GrantCandidatesResponse candidates,
        List<CourseResourceCandidateResponse> courseResources
) {

    static InstallPreviewResponse from(InstallPreview preview) {
        return new InstallPreviewResponse(
                preview.studyServerId(),
                preview.alreadyInstalled(),
                GrantCandidatesResponse.from(preview.candidates()),
                preview.courseResources().stream()
                        .map(CourseResourceCandidateResponse::from)
                        .toList()
        );
    }

    public record GrantCandidatesResponse(
            UUID studyServerId,
            List<ChannelCandidateResponse> studyServerChannels,
            List<CourseCandidateResponse> courses
    ) {
        static GrantCandidatesResponse from(GrantCandidates candidates) {
            return new GrantCandidatesResponse(
                    candidates.studyServerId(),
                    candidates.studyServerChannels().stream()
                            .map(ChannelCandidateResponse::from)
                            .toList(),
                    candidates.courses().stream()
                            .map(CourseCandidateResponse::from)
                            .toList()
            );
        }
    }

    public record ChannelCandidateResponse(UUID id, String name, String kind) {
        static ChannelCandidateResponse from(ChannelCandidate channel) {
            return new ChannelCandidateResponse(channel.id(), channel.name(), channel.kind());
        }
    }

    public record CohortCandidateResponse(UUID id, String name) {
        static CohortCandidateResponse from(CohortCandidate cohort) {
            return new CohortCandidateResponse(cohort.id(), cohort.name());
        }
    }

    public record CourseCandidateResponse(
            UUID id,
            String title,
            List<CohortCandidateResponse> cohorts,
            List<ChannelCandidateResponse> channels
    ) {
        static CourseCandidateResponse from(CourseCandidate course) {
            return new CourseCandidateResponse(
                    course.id(),
                    course.title(),
                    course.cohorts().stream().map(CohortCandidateResponse::from).toList(),
                    course.channels().stream().map(ChannelCandidateResponse::from).toList()
            );
        }
    }

    public record CourseResourceCandidateResponse(
            UUID id,
            UUID courseId,
            String title,
            String fileName,
            boolean aiApproved
    ) {
        static CourseResourceCandidateResponse from(CourseResourceSummary resource) {
            return new CourseResourceCandidateResponse(
                    resource.id(),
                    resource.courseId(),
                    resource.title(),
                    resource.fileName(),
                    resource.aiApproved()
            );
        }
    }
}
