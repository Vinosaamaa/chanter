package com.chanter.message.application;

import java.util.List;
import java.util.UUID;

public interface InstructorDashboardMetricsRepository {

    int countUnansweredSupportQuestions(List<UUID> questionChannelIds);

    int countOpenTaQueueItems(List<UUID> cohortIds);

    int countApprovedFaqs(List<UUID> courseIds);
}
