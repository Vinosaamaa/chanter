package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class GroundedSupportQuestionCancellationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledCatalogNeverStartsRetrievalOrProvider(boolean catalogFails) {
        UUID server = UUID.randomUUID(), channel = UUID.randomUUID(), user = UUID.randomUUID(), course = UUID.randomUUID();
        UUID question = UUID.randomUUID(), install = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        var assistant = mock(StudyAssistantService.class);
        var access = mock(SupportQuestionChannelAccessClient.class);
        var questions = mock(SupportQuestionClient.class);
        var resources = mock(CourseResourceCatalogClient.class);
        var faqs = mock(ApprovedFaqClient.class);
        var runtime = mock(AgentRuntimeService.class);
        var catalog = mock(LlmModelCatalog.class);
        var retrieval = mock(VectorRetrievalService.class);
        var factory = new DefaultListableBeanFactory();
        when(access.requireAccess(channel, user)).thenReturn(new SupportQuestionChannelAccessClient.SupportQuestionChannelAccess(channel, course, server, "q", true, false));
        when(questions.getSupportQuestion(channel, question, user)).thenReturn(new SupportQuestionClient.SupportQuestion(question, channel, user, "How does a queue work?", "UNANSWERED", Instant.now()));
        when(catalog.select("source-only", course)).thenReturn("source-only");
        when(assistant.findPresence(server, user)).thenReturn(new StudyAssistantService.Presence(server, true, List.of(
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_CHANNEL, channel),
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_RESOURCE, first),
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_RESOURCE, second))));
        when(resources.listAiApprovedCourseResources(course, user)).thenReturn(List.of(
                new CourseResourceCatalogClient.CourseResourceSummary(first, course, "First", "first.md", true),
                new CourseResourceCatalogClient.CourseResourceSummary(second, course, "Second", "second.md", true)));
        var service = new GroundedSupportQuestionService(assistant, access, questions, resources, faqs,
                retrieval, factory.getBeanProvider(RagGroundingEngine.class),
                runtime, catalog, mock(AiEvidenceAuthorization.class), mock(AiQuotaEnforcementService.class),
                mock(StudyAssistantAnswerPersistenceService.class), mock(StudyAssistantAnswerRepository.class), Clock.systemUTC(), "rag", 5);
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            when(resources.listAiApprovedCourseResources(course,user)).thenAnswer(call -> {
                execution.cancel();
                if (catalogFails) execution.check();
                return List.of();
            });
            when(runtime.orchestrate(any(), any(), any(), any(), any())).thenAnswer(call -> {
                execution.check(); return null;
            });
            assertThatThrownBy(() -> service.answerSupportQuestion(channel, question, user, "source-only", null, execution, ignored -> {}))
                    .isInstanceOf(LlmProviderException.class).hasMessageContaining("cancelled");
            verifyNoInteractions(retrieval, faqs, runtime);
        }
    }
}
