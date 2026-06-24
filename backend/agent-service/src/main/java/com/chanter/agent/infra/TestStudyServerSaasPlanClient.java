package com.chanter.agent.infra;

import com.chanter.agent.application.StudyServerSaasPlanClient;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestStudyServerSaasPlanClient implements StudyServerSaasPlanClient {

    private final Map<UUID, StudyServerSaasPlan> plans = new HashMap<>();

    public void registerPlan(UUID studyServerId, String planTier, int aiInvocationLimit) {
        plans.put(studyServerId, new StudyServerSaasPlan(studyServerId, planTier, aiInvocationLimit));
    }

    public void clear() {
        plans.clear();
    }

    @Override
    public StudyServerSaasPlan fetchPlan(UUID studyServerId) {
        StudyServerSaasPlan plan = plans.get(studyServerId);
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
        }
        return plan;
    }
}
