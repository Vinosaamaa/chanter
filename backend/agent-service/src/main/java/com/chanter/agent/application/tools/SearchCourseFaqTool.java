package com.chanter.agent.application.tools;

import com.chanter.agent.application.ApprovedFaqClient;
import com.chanter.agent.application.ApprovedFaqClient.ApprovedFaqSummary;
import com.chanter.agent.application.AssistantGrantScopeService.GrantScope;
import com.chanter.agent.domain.GrantType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SearchCourseFaqTool implements AssistantTool {

    private final ApprovedFaqClient approvedFaqClient;

    public SearchCourseFaqTool(ApprovedFaqClient approvedFaqClient) {
        this.approvedFaqClient = approvedFaqClient;
    }

    @Override
    public String name() {
        return "search_course_faq";
    }

    @Override
    public String description() {
        return "Search approved course FAQs. Requires a COURSE grant or at least one COURSE_RESOURCE grant for the course.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "Search text matched against FAQ questions and answers"
                        )
                ),
                "required", List.of("query"),
                "additionalProperties", false
        );
    }

    @Override
    public Object invoke(GrantScope scope, Map<String, Object> arguments) {
        boolean courseGranted = scope.grants().stream()
                .anyMatch(grant -> grant.grantType() == GrantType.COURSE
                        && grant.grantTargetId().equals(scope.courseId()));
        if (!courseGranted && scope.grantedResourceIds().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "FAQ search requires a course or resource grant"
            );
        }

        Object rawQuery = arguments.get("query");
        String query = rawQuery == null ? "" : String.valueOf(rawQuery).trim();
        if (query.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }

        List<Map<String, Object>> faqs = approvedFaqClient
                .searchApprovedFaqs(scope.courseId(), scope.viewerUserId(), query)
                .stream()
                .map(SearchCourseFaqTool::toMap)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", scope.courseId().toString());
        result.put("query", query);
        result.put("faqs", faqs);
        return result;
    }

    private static Map<String, Object> toMap(ApprovedFaqSummary faq) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", faq.id().toString());
        map.put("courseId", faq.courseId().toString());
        map.put("question", faq.question());
        map.put("answer", faq.answer());
        return map;
    }
}
