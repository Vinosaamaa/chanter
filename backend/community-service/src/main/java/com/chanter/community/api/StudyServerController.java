package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.StudyServerNavigationService;
import com.chanter.community.application.StudyServerService;
import com.chanter.community.domain.StudyServer;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers")
public class StudyServerController {

    private final StudyServerService studyServerService;
    private final StudyServerNavigationService studyServerNavigationService;

    public StudyServerController(
            StudyServerService studyServerService,
            StudyServerNavigationService studyServerNavigationService
    ) {
        this.studyServerService = studyServerService;
        this.studyServerNavigationService = studyServerNavigationService;
    }

    @PostMapping
    public ResponseEntity<StudyServerResponse> createStudyServer(
            @Valid @RequestBody CreateStudyServerRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID ownerUserId
    ) {
        StudyServer studyServer = studyServerService.createStudyServer(request.name(), ownerUserId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(studyServer.id())
                .toUri();

        return ResponseEntity.created(location).body(StudyServerResponse.from(studyServer));
    }

    @GetMapping
    public List<AccessibleStudyServerResponse> listAccessibleStudyServers(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return studyServerNavigationService.listAccessibleStudyServers(userId).stream()
                .map(AccessibleStudyServerResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public StudyServerResponse getStudyServer(@PathVariable UUID id) {
        return studyServerService.findStudyServer(id)
                .map(StudyServerResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
    }

    @GetMapping("/{id}/navigation")
    public StudyServerNavigationResponse getStudyServerNavigation(
            @PathVariable UUID id,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return StudyServerNavigationResponse.from(studyServerNavigationService.findNavigation(id, userId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStudyServer(
            @PathVariable UUID id,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID requesterUserId
    ) {
        studyServerService.deleteStudyServer(id, requesterUserId);
    }
}
