package com.chanter.agent.api;

import com.chanter.agent.application.ResourceIngestionService;
import com.chanter.agent.application.ResourceIngestionService.IngestResult;
import com.chanter.agent.domain.ResourceChunk;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/internal/resource-chunks")
public class InternalResourceIngestionController {

    private final ResourceIngestionService resourceIngestionService;
    private final byte[] internalServiceToken;

    public InternalResourceIngestionController(
            ResourceIngestionService resourceIngestionService,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.resourceIngestionService = resourceIngestionService;
        this.internalServiceToken = internalServiceToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResponse ingest(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @Valid @RequestBody IngestRequest request
    ) {
        requireInternalService(serviceToken);
        byte[] content;
        try {
            content = Base64.getDecoder().decode(request.contentBase64());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentBase64 must be valid Base64", exception);
        }

        IngestResult result = resourceIngestionService.ingest(
                request.courseId(),
                request.resourceId(),
                request.fileName(),
                content
        );
        return IngestResponse.from(result);
    }

    @DeleteMapping("/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @PathVariable UUID resourceId
    ) {
        requireInternalService(serviceToken);
        resourceIngestionService.deleteByResourceId(resourceId);
    }

    @GetMapping("/{resourceId}")
    public ChunkListResponse list(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @PathVariable UUID resourceId
    ) {
        requireInternalService(serviceToken);
        List<ResourceChunk> chunks = resourceIngestionService.listByResourceId(resourceId);
        return new ChunkListResponse(chunks.stream().map(ChunkResponse::from).toList());
    }

    private void requireInternalService(String presentedToken) {
        byte[] presented = presentedToken == null
                ? new byte[0]
                : presentedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalServiceToken, presented)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        }
    }

    public record IngestRequest(
            @NotNull UUID courseId,
            @NotNull UUID resourceId,
            @NotBlank String fileName,
            @NotBlank String contentBase64
    ) {
    }

    public record IngestResponse(
            UUID resourceId,
            UUID courseId,
            int chunkCount,
            String contentSha256,
            boolean empty
    ) {
        static IngestResponse from(IngestResult result) {
            return new IngestResponse(
                    result.resourceId(),
                    result.courseId(),
                    result.chunkCount(),
                    result.contentSha256(),
                    result.empty()
            );
        }
    }

    public record ChunkListResponse(List<ChunkResponse> chunks) {
    }

    public record ChunkResponse(
            UUID id,
            UUID resourceId,
            UUID courseId,
            int chunkIndex,
            int startOffset,
            int endOffset,
            String contentText,
            String contentSha256,
            String fileName
    ) {
        static ChunkResponse from(ResourceChunk chunk) {
            return new ChunkResponse(
                    chunk.id(),
                    chunk.resourceId(),
                    chunk.courseId(),
                    chunk.chunkIndex(),
                    chunk.startOffset(),
                    chunk.endOffset(),
                    chunk.contentText(),
                    chunk.contentSha256(),
                    chunk.fileName()
            );
        }
    }
}
