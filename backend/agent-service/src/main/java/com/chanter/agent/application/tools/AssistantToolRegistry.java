package com.chanter.agent.application.tools;

import com.chanter.agent.application.AssistantGrantScopeService;
import com.chanter.agent.application.AssistantGrantScopeService.GrantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantToolRegistry {

    private final Map<String, AssistantTool> toolsByName;
    private final AssistantGrantScopeService grantScopeService;

    public AssistantToolRegistry(List<AssistantTool> tools, AssistantGrantScopeService grantScopeService) {
        Map<String, AssistantTool> mapped = new LinkedHashMap<>();
        for (AssistantTool tool : tools) {
            if (mapped.put(tool.name(), tool) != null) {
                throw new IllegalStateException("Duplicate assistant tool name: " + tool.name());
            }
        }
        this.toolsByName = Map.copyOf(mapped);
        this.grantScopeService = grantScopeService;
    }

    public List<ToolDescriptor> listTools() {
        return toolsByName.values().stream()
                .map(tool -> new ToolDescriptor(tool.name(), tool.description(), tool.inputSchema()))
                .toList();
    }

    public Object invoke(
            String toolName,
            UUID studyServerId,
            UUID courseId,
            UUID viewerUserId,
            UUID channelId,
            Map<String, Object> arguments
    ) {
        AssistantTool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown assistant tool: " + toolName);
        }
        GrantScope scope = grantScopeService.requireScope(studyServerId, courseId, viewerUserId, channelId);
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        return tool.invoke(scope, safeArguments);
    }

    public record ToolDescriptor(String name, String description, Map<String, Object> inputSchema) {
    }
}
