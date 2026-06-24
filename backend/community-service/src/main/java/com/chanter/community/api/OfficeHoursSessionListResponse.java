package com.chanter.community.api;

import com.chanter.community.domain.OfficeHoursSession;
import java.util.List;

public record OfficeHoursSessionListResponse(List<OfficeHoursSessionResponse> officeHoursSessions) {
    static OfficeHoursSessionListResponse from(List<OfficeHoursSession> sessions) {
        return new OfficeHoursSessionListResponse(
                sessions.stream().map(OfficeHoursSessionResponse::from).toList()
        );
    }
}
