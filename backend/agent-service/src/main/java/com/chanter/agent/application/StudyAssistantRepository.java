package com.chanter.agent.application;

import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import com.chanter.agent.domain.StudyAssistantInstall;
import com.chanter.agent.domain.ConfirmedGrant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyAssistantRepository {

    Optional<StudyAssistantInstall> findInstallByStudyServerId(UUID studyServerId);

    StudyAssistantInstall saveInstall(StudyAssistantInstall install, List<ConfirmedGrant> grants);

    List<StudyAssistantGrant> findGrantsByInstallId(UUID installId);
}
