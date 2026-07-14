package com.chanter.community.api;

import com.chanter.community.domain.OfficeHoursParticipant;
import java.util.List;

public record OfficeHoursParticipantListResponse(List<OfficeHoursParticipantResponse> participants) {
    static OfficeHoursParticipantListResponse from(List<OfficeHoursParticipant> participants) {
        return new OfficeHoursParticipantListResponse(
                participants.stream().map(OfficeHoursParticipantResponse::from).toList()
        );
    }
}
