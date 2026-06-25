package com.chanter.community.api;

import com.chanter.community.domain.StudyServerNavigation;
import java.util.UUID;

public record StudyServerNavigationResponse(
        UUID studyServerId,
        String studyServerName,
        java.util.List<StudyAssistantGrantCandidatesResponse.ChannelResponse> studyServerChannels,
        java.util.List<StudyAssistantGrantCandidatesResponse.CourseResponse> courses
) {

    static StudyServerNavigationResponse from(StudyServerNavigation navigation) {
        return new StudyServerNavigationResponse(
                navigation.studyServerId(),
                navigation.studyServerName(),
                navigation.studyServerChannels().stream()
                        .map(StudyAssistantGrantCandidatesResponse.ChannelResponse::fromStudyServerChannel)
                        .toList(),
                navigation.courses().stream()
                        .map(StudyAssistantGrantCandidatesResponse.CourseResponse::from)
                        .toList()
        );
    }
}
