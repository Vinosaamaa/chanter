package com.chanter.agent.application.tools;

import com.chanter.agent.application.AssistantGrantScopeService;
import com.chanter.agent.application.AssistantGrantScopeService.GrantScope;
import com.chanter.agent.application.ResourceChunkRepository;
import com.chanter.agent.domain.ResourceChunk;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FetchResourceChunkTool implements AssistantTool {

    private final AssistantGrantScopeService grantScopeService;
    private final ResourceChunkRepository resourceChunkRepository;

    public FetchResourceChunkTool(
            AssistantGrantScopeService grantScopeService,
            ResourceChunkRepository resourceChunkRepository
    ) {
        this.grantScopeService = grantScopeService;
        this.resourceChunkRepository = resourceChunkRepository;
    }

    @Override
    public String name() {
        return "fetch_resource_chunk";
    }

    @Override
    public String description() {
        return "Fetch one granted resource chunk by resourceId + chunkIndex, or by chunkId. Out-of-grant resources are rejected.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("resourceId", Map.of("type", "string", "description", "Course resource UUID"));
        properties.put("chunkIndex", Map.of("type", "integer", "description", "Zero-based chunk index"));
        properties.put("chunkId", Map.of("type", "string", "description", "Chunk UUID (optional alternative to resourceId+chunkIndex)"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "additionalProperties", false
        );
    }

    @Override
    public Object invoke(GrantScope scope, Map<String, Object> arguments) {
        UUID chunkId = parseUuid(arguments.get("chunkId"), "chunkId");
        if (chunkId != null) {
            ResourceChunk chunk = resourceChunkRepository.findById(chunkId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chunk not found"));
            grantScopeService.requireResourceGranted(scope, chunk.resourceId());
            if (!chunk.courseId().equals(scope.courseId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chunk course does not match tool scope");
            }
            return toMap(chunk);
        }

        UUID resourceId = parseUuid(arguments.get("resourceId"), "resourceId");
        if (resourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceId or chunkId is required");
        }
        grantScopeService.requireResourceGranted(scope, resourceId);

        Integer chunkIndex = parseInteger(arguments.get("chunkIndex"));
        if (chunkIndex == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkIndex is required when using resourceId");
        }

        List<ResourceChunk> chunks = resourceChunkRepository.findByResourceId(resourceId);
        return chunks.stream()
                .filter(chunk -> chunk.chunkIndex() == chunkIndex)
                .findFirst()
                .map(FetchResourceChunkTool::toMap)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chunk not found"));
    }

    private static Map<String, Object> toMap(ResourceChunk chunk) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", chunk.id().toString());
        map.put("resourceId", chunk.resourceId().toString());
        map.put("courseId", chunk.courseId().toString());
        map.put("chunkIndex", chunk.chunkIndex());
        map.put("startOffset", chunk.startOffset());
        map.put("endOffset", chunk.endOffset());
        map.put("contentText", chunk.contentText());
        map.put("fileName", chunk.fileName());
        return map;
    }

    private static UUID parseUuid(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be a UUID", exception);
        }
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkIndex must be an integer", exception);
        }
    }
}
