package com.chanter.agent.application;

import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.chanter.agent.domain.GrantType;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Revalidates the current authorization and source text at the generation/publication boundary. */
@Service
public class AiEvidenceAuthorization {
    private final SupportQuestionChannelAccessClient access;
    private final StudyAssistantService assistant;
    private final CourseResourceCatalogClient resources;
    private final CourseResourceContentClient content;
    private final ApprovedFaqClient faqs;
    public AiEvidenceAuthorization(SupportQuestionChannelAccessClient access, StudyAssistantService assistant,
            CourseResourceCatalogClient resources, CourseResourceContentClient content, ApprovedFaqClient faqs) {
        this.access = access; this.assistant = assistant; this.resources = resources; this.content = content; this.faqs = faqs;
    }
    public void requireCurrent(UUID channel, UUID user, List<SourceCitation> citations) {
        var scope = access.requireAccess(channel, user);
        var presence = assistant.findPresence(scope.studyServerId(), user);
        if (!presence.installed() || presence.grants().stream().noneMatch(g -> g.grantType() == GrantType.COURSE_CHANNEL && g.grantTargetId().equals(channel))) denied();
        if (citations.isEmpty()) return;
        Set<UUID> granted = presence.grants().stream().filter(g -> g.grantType() == GrantType.COURSE_RESOURCE).map(g -> g.grantTargetId()).collect(Collectors.toSet());
        Map<UUID, CourseResourceCatalogClient.CourseResourceSummary> approved = new HashMap<>();
        for (var resource : resources.listAiApprovedCourseResources(scope.courseId(), user)) {
            if (resource.aiApproved() && resource.courseId().equals(scope.courseId()) && granted.contains(resource.id())) approved.put(resource.id(), resource);
        }
        Map<UUID, String> currentText = new HashMap<>();
        for (var citation : citations) {
            if (approved.containsKey(citation.resourceId()) && !currentText.containsKey(citation.resourceId())) {
                byte[] bytes = content.downloadContent(citation.resourceId(), user);
                if (bytes == null || bytes.length > 2_000_000) denied();
                currentText.put(citation.resourceId(), new String(bytes, StandardCharsets.UTF_8));
            }
        }
        if (citations.stream().anyMatch(c -> !approved.containsKey(c.resourceId()))) {
            for (var faq : faqs.listApprovedFaqs(scope.courseId(), user)) {
                if (faq.question() != null && faq.answer() != null) currentText.putIfAbsent(faq.id(), faq.question() + "\n\n" + faq.answer());
            }
        }
        for (var citation : citations) {
            String text = currentText.get(citation.resourceId());
            String excerpt = plainExcerpt(citation.excerpt());
            if (text == null || excerpt.isBlank() || !text.contains(excerpt)) denied();
        }
    }
    public static String plainExcerpt(String excerpt) {
        if (excerpt == null) return "";
        String text = excerpt.replaceFirst("^\\[offsets [0-9]+-[0-9]+]\\s*", "");
        return text.endsWith("…") ? text.substring(0, text.length() - 1).stripTrailing() : text;
    }
    private static void denied() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The answer's evidence is no longer approved or available"); }
}
