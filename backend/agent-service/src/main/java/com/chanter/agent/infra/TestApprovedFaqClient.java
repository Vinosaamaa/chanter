package com.chanter.agent.infra;

import com.chanter.agent.application.ApprovedFaqClient;
import com.chanter.agent.application.ApprovedFaqClient.ApprovedFaqSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestApprovedFaqClient implements ApprovedFaqClient {

    private final Map<String, List<ApprovedFaqSummary>> approvedFaqsByCourse = new ConcurrentHashMap<>();

    public void registerApprovedFaq(UUID courseId, UUID viewerUserId, ApprovedFaqSummary approvedFaq) {
        approvedFaqsByCourse.computeIfAbsent(key(courseId, viewerUserId), ignored -> new ArrayList<>())
                .add(approvedFaq);
    }

    public void clear() {
        approvedFaqsByCourse.clear();
    }

    @Override
    public List<ApprovedFaqSummary> listApprovedFaqs(UUID courseId, UUID viewerUserId) {
        return List.copyOf(approvedFaqsByCourse.getOrDefault(key(courseId, viewerUserId), List.of()));
    }

    private static String key(UUID courseId, UUID viewerUserId) {
        return courseId + ":" + viewerUserId;
    }
}
