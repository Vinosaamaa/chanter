package com.chanter.agent.api;

import com.chanter.agent.application.EmbeddingModelRouter;
import com.chanter.agent.application.EmbeddingRebuildJobs;
import com.chanter.agent.application.EmbeddingVersionStore;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Private operator controls; provider URLs, keys and model files cannot be supplied through this API. */
@RestController
@RequestMapping("/api/v1/internal/embedding-versions")
public class InternalEmbeddingVersionsController {
    private final EmbeddingVersionStore versions;
    private final EmbeddingModelRouter models;
    private final EmbeddingRebuildJobs jobs;
    private final byte[] token;
    public InternalEmbeddingVersionsController(EmbeddingVersionStore versions,EmbeddingModelRouter models,
            EmbeddingRebuildJobs jobs,@Value("${chanter.internal-service-token}") String token) {
        this.versions=versions;this.models=models;this.jobs=jobs;this.token=InternalServiceTokens.requireBytes(token);
    }
    public record Status(EmbeddingVersionStore.State serving,List<EmbeddingVersionStore.Progress> models) {}
    public record Selection(@NotBlank @Size(max=160) String modelId) {}
    public record Retry(@jakarta.validation.constraints.NotNull UUID resourceId,@NotBlank @Size(max=160) String modelId) {}
    @GetMapping public Status status(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String supplied) {
        authenticate(supplied);return new Status(versions.state(),versions.progress());
    }
    @PostMapping("/stage") public Status stage(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String supplied,
            @Valid @RequestBody Selection selection) {
        authenticate(supplied);change(()->{models.client(selection.modelId());versions.stage(selection.modelId());});return status(supplied);
    }
    @PostMapping("/activate") public Status activate(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String supplied,
            @Valid @RequestBody Selection selection) {
        authenticate(supplied);change(()->{models.client(selection.modelId());versions.activate(selection.modelId());});return status(supplied);
    }
    @PostMapping("/rollback") public Status rollback(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String supplied) {
        authenticate(supplied);change(versions::rollback);return status(supplied);
    }
    @PostMapping("/discard") public Status discard(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String supplied,
            @Valid @RequestBody Selection selection) {
        authenticate(supplied);change(()->versions.discard(selection.modelId()));return status(supplied);
    }
    @PostMapping("/retry") public Status retry(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String supplied,
            @Valid @RequestBody Retry retry) {
        authenticate(supplied);change(()->{models.client(retry.modelId());jobs.retryFailed(retry.resourceId(),retry.modelId());});return status(supplied);
    }
    private void authenticate(String supplied) {
        if(!MessageDigest.isEqual(token,supplied==null?new byte[0]:supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Internal service authentication required");
    }
    private static void change(Runnable action) {
        try { action.run(); } catch(IllegalStateException|IllegalArgumentException rejected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Embedding version transition is not ready");
        }
    }
}
