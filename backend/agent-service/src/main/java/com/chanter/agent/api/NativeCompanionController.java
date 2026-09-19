package com.chanter.agent.api;

import com.chanter.agent.application.GroundedSupportQuestionService;
import com.chanter.agent.application.NativeCapabilitySigner;
import com.chanter.agent.application.NativeCompanionService;
import com.chanter.agent.infra.NativeSessionClient;
import com.chanter.common.auth.AuthHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NativeCompanionController {
    private final NativeCapabilitySigner signer;
    private final NativeCompanionService companion;
    private final NativeSessionClient sessions;
    private final GroundedSupportQuestionService questions;
    public NativeCompanionController(NativeCapabilitySigner signer, NativeCompanionService companion,
            NativeSessionClient sessions, GroundedSupportQuestionService questions) {
        this.signer = signer; this.companion = companion; this.sessions = sessions; this.questions = questions;
    }
    @GetMapping("/api/v1/native-companion/configuration")
    public ResponseEntity<Configuration> configuration(@RequestHeader(AuthHeaders.USER_ID) UUID user,
            @RequestHeader(AuthHeaders.AUTHORIZATION) String authorization) {
        if (signer.available()) sessions.requireActive(authorization, user);
        return noStore(new Configuration(signer.available(), "codex", "quoted-evidence", signer.origin(), signer.publicKeyPem(), signer.models()));
    }
    @PostMapping("/api/v1/native-companion/pair")
    public ResponseEntity<NativeCapabilitySigner.Ticket> pair(@RequestHeader(AuthHeaders.USER_ID) UUID user,
            @RequestHeader(AuthHeaders.AUTHORIZATION) String authorization, @RequestHeader(value = "Origin", required = false) String origin,
            @Valid @RequestBody PairRequest request) {
        return noStore(companion.pair(user, authorization, origin, request.installationId(), request.approved()));
    }
    @PostMapping("/api/v1/native-companion/status-ticket")
    public ResponseEntity<NativeCapabilitySigner.Ticket> status(@RequestHeader(AuthHeaders.USER_ID) UUID user,
            @RequestHeader(AuthHeaders.AUTHORIZATION) String authorization, @RequestHeader(value = "Origin", required = false) String origin,
            @Valid @RequestBody StatusRequest request) {
        return noStore(companion.status(user, authorization, origin, request.installationId()));
    }
    @PostMapping("/api/v1/course-channels/{channel}/support-questions/{question}/native-request")
    public ResponseEntity<NativeCompanionService.Issued> issue(@PathVariable UUID channel, @PathVariable UUID question,
            @RequestHeader(AuthHeaders.USER_ID) UUID user, @RequestHeader(AuthHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "Origin", required = false) String origin, @Valid @RequestBody StudyRequest request) {
        return noStore(companion.issue(channel, question, user, authorization, origin, request.installationId(), request.model(), request.exportApproved()));
    }
    @PostMapping("/api/v1/course-channels/{channel}/support-questions/{question}/native-results/{requestId}")
    public ResponseEntity<AssistantAnswerResponse> accept(@PathVariable UUID channel, @PathVariable UUID question, @PathVariable UUID requestId,
            @RequestHeader(AuthHeaders.USER_ID) UUID user, @RequestHeader(AuthHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "Origin", required = false) String origin, @Valid @RequestBody ResultRequest request) {
        var answer = companion.accept(channel, question, requestId, user, authorization, origin, request.installationId(), request.text(),
                request.usage() == null ? null : request.usage().inputTokens(), request.usage() == null ? null : request.usage().outputTokens());
        var view = questions.toAnswerView(answer, user);
        return noStore(AssistantAnswerResponse.from(answer, answer.handoffRecommended() ? "AI_LOW_CONFIDENCE" : "AI_ANSWERED",
                view.audit(), view.helpfulMarked(), view.helpfulCount()));
    }
    private static <T> ResponseEntity<T> noStore(T value) { return ResponseEntity.ok().header("Cache-Control", "no-store").body(value); }
    public record PairRequest(@NotNull UUID installationId, boolean approved) {}
    public record StatusRequest(@NotNull UUID installationId) {}
    public record StudyRequest(@NotNull UUID installationId, @NotNull @Size(min = 1, max = 128) String model, boolean exportApproved) {}
    public record ResultRequest(@NotNull UUID installationId, @NotNull @Size(max = 8192) String text, ClientUsage usage) {}
    public record ClientUsage(Integer inputTokens, Integer outputTokens) {}
    public record Configuration(boolean available, String provider, String mode, String origin, String publicKey, Set<String> models) {}
}
