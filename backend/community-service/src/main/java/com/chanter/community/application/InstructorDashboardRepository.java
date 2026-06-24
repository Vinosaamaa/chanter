package com.chanter.community.application;

import java.util.UUID;

public interface InstructorDashboardRepository {

    CommunityDashboardMetrics findCommunityMetrics(UUID studyServerId);
}
