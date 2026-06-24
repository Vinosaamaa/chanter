package com.chanter.community.application;

import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficeHoursRepository {

    OfficeHoursSession saveSession(OfficeHoursSession session);

    Optional<OfficeHoursSession> findSessionById(UUID sessionId);

    List<OfficeHoursSession> findSessionsByCohortId(UUID cohortId);

    OfficeHoursSession updateSessionStatus(UUID sessionId, String status);

    OfficeHoursWaitlistEntry saveWaitlistEntry(OfficeHoursWaitlistEntry entry);

    Optional<OfficeHoursWaitlistEntry> findWaitlistEntry(UUID sessionId, UUID learnerUserId);

    List<OfficeHoursWaitlistEntry> findWaitlistEntries(UUID sessionId);

    Optional<OfficeHoursWaitlistEntry> findNextWaitingEntry(UUID sessionId);

    OfficeHoursWaitlistEntry updateWaitlistStatus(UUID sessionId, UUID learnerUserId, String status);

    Optional<UUID> findStudyServerIdForCohort(UUID cohortId);
}
