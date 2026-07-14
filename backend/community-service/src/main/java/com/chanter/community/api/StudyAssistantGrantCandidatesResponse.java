package com.chanter.community.api;

import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.GrantCandidateCohort;
import com.chanter.community.domain.GrantCandidateCourse;
import com.chanter.community.domain.StudyAssistantGrantCandidates;
import com.chanter.community.domain.StudyAssistantViewerScope;
import com.chanter.community.domain.StudyServerChannel;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record StudyAssistantGrantCandidatesResponse(
        UUID studyServerId,
        List<ChannelResponse> studyServerChannels,
        List<CourseResponse> courses
) {

    static StudyAssistantGrantCandidatesResponse from(StudyAssistantGrantCandidates candidates) {
        return new StudyAssistantGrantCandidatesResponse(
                candidates.studyServerId(),
                candidates.studyServerChannels().stream()
                        .sorted(Comparator.comparingInt(StudyServerChannel::position))
                        .map(ChannelResponse::fromStudyServerChannel)
                        .toList(),
                candidates.courses().stream()
                        .map(CourseResponse::from)
                        .toList()
        );
    }

    public record ChannelResponse(UUID id, UUID cohortId, String name, String kind) {
        static ChannelResponse fromStudyServerChannel(StudyServerChannel channel) {
            return new ChannelResponse(channel.id(), null, channel.name(), channel.kind().name());
        }

        static ChannelResponse fromCourseChannel(CourseChannel channel) {
            return new ChannelResponse(channel.id(), channel.cohortId(), channel.name(), channel.kind().name());
        }
    }

    public record CohortResponse(UUID id, String name) {
        static CohortResponse from(GrantCandidateCohort cohort) {
            return new CohortResponse(cohort.id(), cohort.name());
        }
    }

    public record CourseResponse(
            UUID id,
            String title,
            List<CohortResponse> cohorts,
            List<ChannelResponse> channels
    ) {
        static CourseResponse from(GrantCandidateCourse course) {
            return new CourseResponse(
                    course.id(),
                    course.title(),
                    course.cohorts().stream().map(CohortResponse::from).toList(),
                    course.channels().stream()
                            .sorted(Comparator.comparingInt(CourseChannel::position))
                            .map(ChannelResponse::fromCourseChannel)
                            .toList()
            );
        }
    }
}
