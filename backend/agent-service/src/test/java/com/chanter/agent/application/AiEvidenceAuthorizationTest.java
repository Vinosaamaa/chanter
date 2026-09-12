package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AiEvidenceAuthorizationTest {
    @Test void deletedResourceExcludedFromCatalogCannotPublishStoredEvidenceOrDownloadItsOldContent() {
        var access = mock(SupportQuestionChannelAccessClient.class);
        var assistant = mock(StudyAssistantRepository.class);
        var resources = mock(CourseResourceCatalogClient.class);
        var content = mock(CourseResourceContentClient.class);
        var faqs = mock(ApprovedFaqClient.class);
        UUID user = UUID.randomUUID(), channel = UUID.randomUUID(), course = UUID.randomUUID();
        UUID server = UUID.randomUUID(), resource = UUID.randomUUID(), install = UUID.randomUUID();
        when(access.requireAccess(channel, user)).thenReturn(new SupportQuestionChannelAccessClient.SupportQuestionChannelAccess(channel, course, server, "q", true, false));
        when(assistant.findInstallByStudyServerId(server)).thenReturn(java.util.Optional.of(
                new com.chanter.agent.domain.StudyAssistantInstall(install, server, user, java.time.Instant.now())));
        when(assistant.findGrantsByInstallId(install)).thenReturn(List.of(
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_CHANNEL, channel),
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_RESOURCE, resource)));
        // The document catalog excludes deleted resources even while an old installation grant/citation remains.
        when(resources.listAiApprovedCourseResources(course, user)).thenReturn(List.of());
        var guard = new AiEvidenceAuthorization(access, assistant, resources, content, faqs);
        assertThatThrownBy(() -> guard.requireCurrent(channel, user, List.of(new SourceCitation(resource, "Removed guide", "Previously approved evidence."))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(content);
    }

    @Test void checksCurrentApprovalAndContentRatherThanTrustingAnOldRetrievedChunk() {
        var access = mock(SupportQuestionChannelAccessClient.class);
        var assistant = mock(StudyAssistantRepository.class);
        var resources = mock(CourseResourceCatalogClient.class);
        var content = mock(CourseResourceContentClient.class);
        var faqs = mock(ApprovedFaqClient.class);
        UUID user = UUID.randomUUID(), channel = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID(), resource = UUID.randomUUID(), install = UUID.randomUUID();
        when(access.requireAccess(channel, user)).thenReturn(new SupportQuestionChannelAccessClient.SupportQuestionChannelAccess(channel, course, server, "q", true, false));
        when(assistant.findInstallByStudyServerId(server)).thenReturn(java.util.Optional.of(
                new com.chanter.agent.domain.StudyAssistantInstall(install, server, user, java.time.Instant.now())));
        when(assistant.findGrantsByInstallId(install)).thenReturn(List.of(
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_CHANNEL, channel),
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_RESOURCE, resource)));
        when(resources.listAiApprovedCourseResources(course, user)).thenReturn(List.of(new CourseResourceSummary(resource, course, "Guide", "guide.md", true)));
        when(content.downloadContent(resource, user)).thenReturn("An authorized queue excerpt.".getBytes(StandardCharsets.UTF_8));
        var guard = new AiEvidenceAuthorization(access, assistant, resources, content, faqs);
        var evidence = List.of(new SourceCitation(resource, "Guide", "[offsets 0-40] An authorized queue excerpt."));
        guard.requireCurrent(channel, user, evidence);
        when(content.downloadContent(resource, user)).thenReturn("A replacement no longer supporting that answer.".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> guard.requireCurrent(channel, user, evidence)).isInstanceOf(ResponseStatusException.class);
        when(resources.listAiApprovedCourseResources(course, user)).thenReturn(List.of());
        assertThatThrownBy(() -> guard.requireCurrent(channel, user, evidence)).isInstanceOf(ResponseStatusException.class);
        when(assistant.findGrantsByInstallId(install)).thenReturn(List.of());
        assertThatThrownBy(() -> guard.requireCurrent(channel, user, List.of())).isInstanceOf(ResponseStatusException.class);
    }
}
