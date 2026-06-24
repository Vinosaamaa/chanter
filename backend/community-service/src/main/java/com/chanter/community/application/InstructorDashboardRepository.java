package com.chanter.community.application;

import com.chanter.community.domain.CommunityDashboardMetrics;
import java.util.UUID;

public interface InstructorDashboardRepository {

    CommunityDashboardMetrics findCommunityMetrics(UUID studyServerId);
}
