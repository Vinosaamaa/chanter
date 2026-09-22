package com.chanter.community.api;

import com.chanter.community.application.LiveKitJoinGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LiveKitJoinAuthorizationController {
    private final LiveKitJoinGuard guard;
    public LiveKitJoinAuthorizationController(LiveKitJoinGuard guard) { this.guard = guard; }

    @GetMapping("/internal/v1/media/join-authorization")
    ResponseEntity<Void> authorize(@RequestHeader(value = "X-Chanter-LiveKit-Token", required = false) String token) {
        try {
            guard.requireJoin(token);
            return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
        } catch (ResponseStatusException denied) {
            return ResponseEntity.status(denied.getStatusCode()).header("Cache-Control", "no-store").build();
        }
    }
}
