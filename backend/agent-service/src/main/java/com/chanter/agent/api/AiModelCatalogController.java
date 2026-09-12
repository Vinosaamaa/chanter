package com.chanter.agent.api;

import com.chanter.agent.application.LlmModelCatalog;
import com.chanter.agent.application.AiAnswerMode;
import com.chanter.agent.application.StudyAssistantService;
import com.chanter.agent.application.SupportQuestionChannelAccessClient;
import com.chanter.agent.domain.GrantType;
import com.chanter.common.auth.AuthHeaders;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AiModelCatalogController {
    private final SupportQuestionChannelAccessClient access;
    private final StudyAssistantService assistant;
    private final LlmModelCatalog catalog;
    public AiModelCatalogController(SupportQuestionChannelAccessClient access, StudyAssistantService assistant, LlmModelCatalog catalog) {
        this.access = access; this.assistant = assistant; this.catalog = catalog;
    }
    @GetMapping("/api/v1/course-channels/{channelId}/assistant-models")
    public Catalog models(@PathVariable UUID channelId, @RequestHeader(AuthHeaders.USER_ID) UUID userId) {
        var scope = access.requireAccess(channelId, userId);
        var presence = assistant.findPresence(scope.studyServerId(), userId);
        if (!presence.installed() || presence.grants().stream().noneMatch(grant -> grant.grantType() == GrantType.COURSE_CHANNEL && grant.grantTargetId().equals(channelId)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI Study Assistant is not granted for this Course Channel");
        var models = catalog.modelsFor(scope.courseId());
        return new Catalog(catalog.select(null, scope.courseId()), models, AiAnswerMode.capabilities(models.size() > 1));
    }
    public record Catalog(String defaultModelId, List<LlmModelCatalog.ModelView> models, List<AiAnswerMode.Capability> answerModes) {}
}
