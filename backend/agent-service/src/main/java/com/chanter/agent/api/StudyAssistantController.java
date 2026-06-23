package com.chanter.agent.api;

import com.chanter.common.ServiceInfo;
import com.chanter.agent.application.StudyAssistantService;
import com.chanter.agent.domain.StudyAssistantInstall;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}/study-assistant")
public class StudyAssistantController {

    private final StudyAssistantService studyAssistantService;

    public StudyAssistantController(StudyAssistantService studyAssistantService) {
        this.studyAssistantService = studyAssistantService;
    }

    @GetMapping("/install-preview")
    public InstallPreviewResponse previewInstall(
            @PathVariable UUID studyServerId,
            @RequestParam UUID instructorUserId
    ) {
        return InstallPreviewResponse.from(
                studyAssistantService.previewInstall(studyServerId, instructorUserId)
        );
    }

    @PostMapping("/install")
    public ResponseEntity<StudyAssistantPresenceResponse> install(
            @PathVariable UUID studyServerId,
            @Valid @RequestBody InstallStudyAssistantRequest request
    ) {
        StudyAssistantInstall install = studyAssistantService.install(
                studyServerId,
                request.instructorUserId(),
                request.toConfirmedGrants()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}/study-assistant")
                .buildAndExpand(install.studyServerId())
                .toUri();

        return ResponseEntity.created(location).body(
                StudyAssistantPresenceResponse.from(
                        studyAssistantService.findPresence(studyServerId, request.instructorUserId())
                )
        );
    }

    @GetMapping
    public StudyAssistantPresenceResponse findPresence(
            @PathVariable UUID studyServerId,
            @RequestParam UUID viewerUserId
    ) {
        return StudyAssistantPresenceResponse.from(
                studyAssistantService.findPresence(studyServerId, viewerUserId)
        );
    }
}
