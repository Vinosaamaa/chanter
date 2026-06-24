package com.chanter.community.api;

import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import java.util.List;

public record OfficeHoursWaitlistListResponse(List<OfficeHoursWaitlistEntryResponse> waitlistEntries) {
    static OfficeHoursWaitlistListResponse from(List<OfficeHoursWaitlistEntry> entries) {
        return new OfficeHoursWaitlistListResponse(
                entries.stream().map(OfficeHoursWaitlistEntryResponse::from).toList()
        );
    }
}
