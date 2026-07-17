package com.chanter.agent.api;

import com.chanter.agent.application.tools.AssistantToolRegistry;
import com.chanter.agent.application.tools.AssistantToolRegistry.ToolDescriptor;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.AuthHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * MCP-compatible tool surface for the Study Assistant.
 * Tools are grant-scoped; callers must present the internal service token.
 */
@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/internal/assistant-tools")
public class InternalAssistantToolsController {

    private final AssistantToolRegistry assistantToolRegistry;
    private final byte[] internalServiceToken;

    public InternalAssistantToolsController(
            AssistantToolRegistry assistantToolRegistry,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.assistantToolRegistry = assistantToolRegistry;
        this.internalServiceToken = InternalServiceTokens.requireBytes(internalServiceToken);
    }

    @GetMapping
    public ToolsListResponse listTools(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken
    ) {
        requireInternalService(serviceToken);
        List<ToolDescriptor> tools = assistantToolRegistry.listTools();
        return new ToolsListResponse(tools.stream().map(ToolSchemaResponse::from).toList());
    }

    @PostMapping("/invoke")
    public ToolInvokeResponse invoke(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @Valid @RequestBody ToolInvokeRequest request
    ) {
        requireInternalService(serviceToken);
        Object result = assistantToolRegistry.invoke(
                request.tool(),
                request.studyServerId(),
                request.courseId(),
                request.viewerUserId(),
                request.channelId(),
                request.arguments() == null ? Map.of() : request.arguments()
        );
        return new ToolInvokeResponse(request.tool(), result);
    }

    /**
     * Minimal MCP JSON-RPC subset: tools/list and tools/call.
     */
    @PostMapping("/mcp")
    public Map<String, Object> mcp(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @RequestBody Map<String, Object> rpc
    ) {
        requireInternalService(serviceToken);
        String method = String.valueOf(rpc.getOrDefault("method", ""));
        Object id = rpc.get("id");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.put("id", id);
        }

        try {
            if ("tools/list".equals(method)) {
                List<Map<String, Object>> tools = assistantToolRegistry.listTools().stream()
                        .map(tool -> {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("name", tool.name());
                            entry.put("description", tool.description());
                            entry.put("inputSchema", tool.inputSchema());
                            return entry;
                        })
                        .toList();
                response.put("result", Map.of("tools", tools));
                return response;
            }

            if ("tools/call".equals(method)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = rpc.get("params") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                String toolName = String.valueOf(params.get("name"));
                @SuppressWarnings("unchecked")
                Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> args
                        ? (Map<String, Object>) args
                        : Map.of();
                UUID studyServerId = requiredUuid(params, "studyServerId");
                UUID courseId = requiredUuid(params, "courseId");
                UUID viewerUserId = requiredUuid(params, "viewerUserId");
                UUID channelId = optionalUuid(params.get("channelId"));
                Object result = assistantToolRegistry.invoke(
                        toolName,
                        studyServerId,
                        courseId,
                        viewerUserId,
                        channelId,
                        arguments
                );
                response.put("result", Map.of(
                        "content", List.of(Map.of("type", "text", "text", String.valueOf(result))),
                        "structuredContent", result,
                        "isError", false
                ));
                return response;
            }

            response.put("error", Map.of("code", -32601, "message", "Method not found: " + method));
            return response;
        } catch (ResponseStatusException exception) {
            response.put("error", Map.of(
                    "code", exception.getStatusCode().value(),
                    "message", exception.getReason() == null ? exception.getMessage() : exception.getReason()
            ));
            return response;
        }
    }

    private void requireInternalService(String presentedToken) {
        byte[] presented = presentedToken == null
                ? new byte[0]
                : presentedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalServiceToken, presented)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        }
    }

    private static UUID requiredUuid(Map<String, Object> params, String field) {
        Object value = params.get(field);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a UUID", exception);
        }
    }

    private static UUID optionalUuid(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelId must be a UUID", exception);
        }
    }

    public record ToolsListResponse(List<ToolSchemaResponse> tools) {
    }

    public record ToolSchemaResponse(String name, String description, Map<String, Object> inputSchema) {
        static ToolSchemaResponse from(ToolDescriptor descriptor) {
            return new ToolSchemaResponse(descriptor.name(), descriptor.description(), descriptor.inputSchema());
        }
    }

    public record ToolInvokeRequest(
            @NotBlank String tool,
            @NotNull UUID studyServerId,
            @NotNull UUID courseId,
            @NotNull UUID viewerUserId,
            UUID channelId,
            Map<String, Object> arguments
    ) {
    }

    public record ToolInvokeResponse(String tool, Object result) {
    }
}
