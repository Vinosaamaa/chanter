package com.chanter.community.infra;

import com.chanter.community.application.SaasPlanRepository;
import com.chanter.community.domain.SaasPlanTier;
import com.chanter.community.domain.StudyServerRole;
import com.chanter.community.domain.StudyServerSaasPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSaasPlanRepository implements SaasPlanRepository {

    private final JdbcClient jdbcClient;

    public JdbcSaasPlanRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyServerSaasPlan> findByStudyServerId(UUID studyServerId) {
        return jdbcClient.sql("""
                        SELECT id, plan_tier
                        FROM study_servers
                        WHERE id = :studyServerId
                        """)
                .param("studyServerId", studyServerId)
                .query((rs, rowNum) -> {
                    SaasPlanTier planTier = SaasPlanTier.valueOf(rs.getString("plan_tier"));
                    return new StudyServerSaasPlan(
                            rs.getObject("id", UUID.class),
                            planTier,
                            planTier.aiInvocationLimit()
                    );
                })
                .optional();
    }

    @Override
    @Transactional
    public boolean updatePlanTierIfOwner(UUID studyServerId, UUID ownerUserId, SaasPlanTier planTier) {
        return jdbcClient.sql("""
                        UPDATE study_servers
                        SET plan_tier = :planTier
                        WHERE id = :studyServerId
                        AND EXISTS (
                            SELECT 1
                            FROM study_server_roles
                            WHERE study_server_id = :studyServerId
                            AND user_id = :ownerUserId
                            AND role = :ownerRole
                        )
                        """)
                .param("studyServerId", studyServerId)
                .param("ownerUserId", ownerUserId)
                .param("planTier", planTier.name())
                .param("ownerRole", StudyServerRole.STUDY_SERVER_OWNER.name())
                .update() > 0;
    }
}
