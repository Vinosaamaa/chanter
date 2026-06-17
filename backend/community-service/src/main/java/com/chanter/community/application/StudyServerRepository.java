package com.chanter.community.application;

import com.chanter.community.domain.StudyServer;
import java.util.Optional;
import java.util.UUID;

public interface StudyServerRepository {

    StudyServer save(StudyServer studyServer);

    Optional<StudyServer> findById(UUID id);
}
