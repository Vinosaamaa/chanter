package com.chanter.agent.infra;

import com.chanter.agent.application.StudyAssistantRepository;
import com.chanter.agent.domain.ConfirmedGrant;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import com.chanter.agent.domain.StudyAssistantInstall;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcStudyAssistantRepository implements StudyAssistantRepository {

    private final JdbcClient jdbcClient;

    public JdbcStudyAssistantRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyAssistantInstall> findInstallByStudyServerId(UUID studyServerId) {
        return jdbcClient.sql("""
                        SELECT id, study_server_id, installed_by_user_id, installed_at
                        FROM study_assistant_installs
                        WHERE study_server_id = :studyServerId
                        """)
                .param("studyServerId", studyServerId)
                .query((rs, rowNum) -> new StudyAssistantInstall(
                        rs.getObject("id", UUID.class),
                        rs.getObject("study_server_id", UUID.class),
                        rs.getObject("installed_by_user_id", UUID.class),
                        rs.getObject("installed_at", OffsetDateTime.class).toInstant()
                ))
                .optional();
    }

    @Override
    @Transactional
    public StudyAssistantInstall saveInstall(StudyAssistantInstall install, List<ConfirmedGrant> grants) {
        try {
            jdbcClient.sql("""
                            INSERT INTO study_assistant_installs (
                                id,
                                study_server_id,
                                installed_by_user_id,
                                installed_at
                            )
                            VALUES (:id, :studyServerId, :installedByUserId, :installedAt)
                            """)
                    .param("id", install.id())
                    .param("studyServerId", install.studyServerId())
                    .param("installedByUserId", install.installedByUserId())
                    .param("installedAt", OffsetDateTime.ofInstant(install.installedAt(), ZoneOffset.UTC))
                    .update();
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI Study Assistant is already installed in this Study Server",
                    exception
            );
        }

        for (ConfirmedGrant grant : grants) {
            jdbcClient.sql("""
                            INSERT INTO study_assistant_grants (
                                id,
                                install_id,
                                grant_type,
                                grant_target_id
                            )
                            VALUES (:id, :installId, :grantType, :grantTargetId)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("installId", install.id())
                    .param("grantType", grant.grantType().name())
                    .param("grantTargetId", grant.grantTargetId())
                    .update();
        }

        return install;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyAssistantGrant> findGrantsByInstallId(UUID installId) {
        return jdbcClient.sql("""
                        SELECT id, install_id, grant_type, grant_target_id
                        FROM study_assistant_grants
                        WHERE install_id = :installId
                        ORDER BY grant_type, grant_target_id
                        """)
                .param("installId", installId)
                .query((rs, rowNum) -> new StudyAssistantGrant(
                        rs.getObject("id", UUID.class),
                        rs.getObject("install_id", UUID.class),
                        GrantType.valueOf(rs.getString("grant_type")),
                        rs.getObject("grant_target_id", UUID.class)
                ))
                .list();
    }
}
