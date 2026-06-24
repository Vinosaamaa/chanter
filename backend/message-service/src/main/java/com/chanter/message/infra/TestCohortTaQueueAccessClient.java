package com.chanter.message.infra;

import com.chanter.message.application.CohortTaQueueAccess;
import com.chanter.message.application.CohortTaQueueAccessClient;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestCohortTaQueueAccessClient implements CohortTaQueueAccessClient {

    private final Map<String, CohortTaQueueAccess> accessRules = new ConcurrentHashMap<>();
    private final Set<UUID> existingCohorts = ConcurrentHashMap.newKeySet();

    public void registerCohort(UUID cohortId, UUID courseId, UUID studyServerId) {
        existingCohorts.add(cohortId);
        accessRules.putIfAbsent(
                key(cohortId, UUID.randomUUID()),
                new CohortTaQueueAccess(cohortId, courseId, studyServerId, false, false)
        );
    }

    public void grantLearnerAdd(UUID cohortId, UUID userId, UUID courseId, UUID studyServerId) {
        existingCohorts.add(cohortId);
        accessRules.put(key(cohortId, userId), new CohortTaQueueAccess(
                cohortId,
                courseId,
                studyServerId,
                true,
                false
        ));
    }

    public void grantInstructorManage(UUID cohortId, UUID userId, UUID courseId, UUID studyServerId) {
        existingCohorts.add(cohortId);
        accessRules.put(key(cohortId, userId), new CohortTaQueueAccess(
                cohortId,
                courseId,
                studyServerId,
                false,
                true
        ));
    }

    public void clear() {
        accessRules.clear();
        existingCohorts.clear();
    }

    @Override
    public CohortTaQueueAccess requireAccess(UUID cohortId, UUID userId) {
        CohortTaQueueAccess access = accessRules.get(key(cohortId, userId));
        if (access != null) {
            return access;
        }
        if (!existingCohorts.contains(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "TA Queue access requires Cohort Enrollment or Instructor role"
        );
    }

    private static String key(UUID cohortId, UUID userId) {
        return cohortId + ":" + userId;
    }
}
