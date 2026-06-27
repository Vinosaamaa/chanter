package com.chanter.search.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.search.application.GlobalSearchService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    public GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @GetMapping("/search")
    public GlobalSearchResponse search(
            @PathVariable UUID studyServerId,
            @RequestParam String q,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return GlobalSearchResponse.from(globalSearchService.search(studyServerId, viewerUserId, q));
    }

    @PostMapping("/search/reindex")
    public ReindexResponse reindex(
            @PathVariable UUID studyServerId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        int indexedDocuments = globalSearchService.reindexStudyServer(studyServerId, viewerUserId);
        return new ReindexResponse(indexedDocuments);
    }
}
