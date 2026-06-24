package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.message.application.TaQueueService;
import com.chanter.message.domain.TaQueueItem;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/cohorts/{cohortId}/ta-queue")
public class TaQueueController {

    private final TaQueueService taQueueService;

    public TaQueueController(TaQueueService taQueueService) {
        this.taQueueService = taQueueService;
    }

    @PostMapping
    public ResponseEntity<TaQueueItemResponse> addToTaQueue(
            @PathVariable UUID cohortId,
            @Valid @RequestBody AddToTaQueueRequest request
    ) {
        TaQueueItem item = taQueueService.addToQueue(
                cohortId,
                request.learnerUserId(),
                request.supportQuestionId(),
                request.channelId()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ServiceInfo.API_V1_PREFIX + "/cohorts/{cohortId}/ta-queue/{itemId}")
                .buildAndExpand(cohortId, item.id())
                .toUri();

        return ResponseEntity.created(location).body(TaQueueItemResponse.from(item));
    }

    @GetMapping
    public TaQueueListResponse listTaQueue(
            @PathVariable UUID cohortId,
            @RequestParam UUID viewerUserId
    ) {
        List<TaQueueItemResponse> items = taQueueService.listQueue(cohortId, viewerUserId)
                .stream()
                .map(TaQueueItemResponse::from)
                .toList();
        return new TaQueueListResponse(items);
    }

    @PatchMapping("/{itemId}/pickup")
    public TaQueueItemResponse pickupTaQueueItem(
            @PathVariable UUID cohortId,
            @PathVariable UUID itemId,
            @Valid @RequestBody TaQueueActorRequest request
    ) {
        return TaQueueItemResponse.from(taQueueService.pickupQueueItem(cohortId, itemId, request.actorUserId()));
    }

    @PatchMapping("/{itemId}/resolve")
    public TaQueueItemResponse resolveTaQueueItem(
            @PathVariable UUID cohortId,
            @PathVariable UUID itemId,
            @Valid @RequestBody TaQueueActorRequest request
    ) {
        return TaQueueItemResponse.from(taQueueService.resolveQueueItem(cohortId, itemId, request.actorUserId()));
    }

    @PatchMapping("/{itemId}/cancel")
    public TaQueueItemResponse cancelTaQueueItem(
            @PathVariable UUID cohortId,
            @PathVariable UUID itemId,
            @Valid @RequestBody TaQueueActorRequest request
    ) {
        return TaQueueItemResponse.from(taQueueService.cancelQueueItem(cohortId, itemId, request.actorUserId()));
    }
}
