package com.chanter.community.infra;

import com.chanter.community.application.InstructorDashboardRepository;
import com.chanter.community.domain.CommunityDashboardMetrics;
import com.chanter.community.domain.OfficeHoursSessionStatus;
import com.chanter.community.domain.OfficeHoursWaitlistStatus;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcInstructorDashboardRepository implements InstructorDashboardRepository {

    private final JdbcClient jdbcClient;

    public JdbcInstructorDashboardRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityDashboardMetrics findCommunityMetrics(UUID studyServerId) {
        int liveSessions = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM office_hours_sessions ohs
                        JOIN cohorts c ON c.id = ohs.cohort_id
                        JOIN courses co ON co.id = c.course_id
                        WHERE co.study_server_id = :studyServerId
                        AND ohs.status = :liveStatus
                        """)
                .param("studyServerId", studyServerId)
                .param("liveStatus", OfficeHoursSessionStatus.LIVE.name())
                .query(Integer.class)
                .single();

        int scheduledSessions = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM office_hours_sessions ohs
                        JOIN cohorts c ON c.id = ohs.cohort_id
                        JOIN courses co ON co.id = c.course_id
                        WHERE co.study_server_id = :studyServerId
                        AND ohs.status = :scheduledStatus
                        """)
                .param("studyServerId", studyServerId)
                .param("scheduledStatus", OfficeHoursSessionStatus.SCHEDULED.name())
                .query(Integer.class)
                .single();

        int waitlistEntries = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM office_hours_waitlist_entries w
                        JOIN office_hours_sessions ohs ON ohs.id = w.session_id
                        JOIN cohorts c ON c.id = ohs.cohort_id
                        JOIN courses co ON co.id = c.course_id
                        WHERE co.study_server_id = :studyServerId
                        AND w.status = :waitingStatus
                        """)
                .param("studyServerId", studyServerId)
                .param("waitingStatus", OfficeHoursWaitlistStatus.WAITING.name())
                .query(Integer.class)
                .single();

        return new CommunityDashboardMetrics(
                studyServerId,
                liveSessions,
                scheduledSessions,
                waitlistEntries
        );
    }
}
