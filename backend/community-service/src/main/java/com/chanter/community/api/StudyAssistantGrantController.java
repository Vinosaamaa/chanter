package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.community.application.StudyAssistantGrantService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}")
public class StudyAssistantGrantController {

    private final StudyAssistantGrantService studyAssistantGrantService;

    public StudyAssistantGrantController(StudyAssistantGrantService studyAssistantGrantService) {
        this.studyAssistantGrantService = studyAssistantGrantService;
    }

    @GetMapping("/study-assistant-grant-candidates")
    public StudyAssistantGrantCandidatesResponse findGrantCandidates(
            @PathVariable UUID studyServerId,
            @RequestParam UUID userId
    ) {
        return StudyAssistantGrantCandidatesResponse.from(
                studyAssistantGrantService.findGrantCandidates(studyServerId, userId)
        );
    }

    @GetMapping("/study-assistant-viewer-scope")
    public StudyAssistantViewerScopeResponse findViewerScope(
            @PathVariable UUID studyServerId,
            @RequestParam UUID userId
    ) {
        return StudyAssistantViewerScopeResponse.from(
                studyAssistantGrantService.findViewerScope(studyServerId, userId)
        );
    }
}
