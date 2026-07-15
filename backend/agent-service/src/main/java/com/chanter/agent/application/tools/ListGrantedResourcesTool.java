package com.chanter.agent.application.tools;

import com.chanter.agent.application.AssistantGrantScopeService.GrantScope;
import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ListGrantedResourcesTool implements AssistantTool {

    @Override
    public String name() {
        return "list_granted_resources";
    }

    @Override
    public String description() {
        return "List AI-approved course resources that the Study Assistant is granted to read for this course.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "additionalProperties", false
        );
    }

    @Override
    public Object invoke(GrantScope scope, Map<String, Object> arguments) {
        List<Map<String, Object>> resources = scope.grantedResources().stream()
                .map(ListGrantedResourcesTool::toMap)
                .toList();
        return Map.of(
                "courseId", scope.courseId().toString(),
                "resources", resources
        );
    }

    private static Map<String, Object> toMap(CourseResourceSummary resource) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", resource.id().toString());
        map.put("courseId", resource.courseId().toString());
        map.put("title", resource.title());
        map.put("fileName", resource.fileName());
        map.put("aiApproved", resource.aiApproved());
        return map;
    }
}
