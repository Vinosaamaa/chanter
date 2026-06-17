package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.community.application.StudyServerService;
import com.chanter.community.domain.StudyServer;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers")
public class StudyServerController {

    private final StudyServerService studyServerService;

    public StudyServerController(StudyServerService studyServerService) {
        this.studyServerService = studyServerService;
    }

    @PostMapping
    public ResponseEntity<StudyServerResponse> createStudyServer(
            @Valid @RequestBody CreateStudyServerRequest request
    ) {
        StudyServer studyServer = studyServerService.createStudyServer(request.name(), request.ownerUserId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(studyServer.id())
                .toUri();

        return ResponseEntity.created(location).body(StudyServerResponse.from(studyServer));
    }

    @GetMapping("/{id}")
    public StudyServerResponse getStudyServer(@PathVariable UUID id) {
        return studyServerService.findStudyServer(id)
                .map(StudyServerResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
    }
}
