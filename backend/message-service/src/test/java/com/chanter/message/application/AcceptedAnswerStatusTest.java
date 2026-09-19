package com.chanter.message.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.server.ResponseStatusException;

class AcceptedAnswerStatusTest {
    private final UUID channel = UUID.randomUUID(), question = UUID.randomUUID(), author = UUID.randomUUID();
    private final SupportQuestionRepository repository = mock(SupportQuestionRepository.class);
    private final CourseChannelAccessClient access = mock(CourseChannelAccessClient.class);
    private final NotificationClient notifications = mock(NotificationClient.class);
    private final SupportQuestionService service = new SupportQuestionService(repository, access,
            mock(SupportQuestionWriter.class), mock(SupportQuestionReplyRepository.class),
            mock(TaQueueRepository.class), notifications, Clock.systemUTC());

    @Test
    void acceptedOutcomeAppliesOnceAndReplayDoesNotNotifyAgain() {
        authorize(true);
        var unanswered = question(SupportQuestionStatus.UNANSWERED, author);
        var answered = question(SupportQuestionStatus.AI_ANSWERED, author);
        when(repository.findByIdAndChannelId(channel, question)).thenReturn(Optional.of(unanswered), Optional.of(answered));
        when(repository.updateStatus(question, SupportQuestionStatus.UNANSWERED, SupportQuestionStatus.AI_ANSWERED)).thenReturn(true);

        assertThat(reconcile(SupportQuestionStatus.AI_ANSWERED)).isEqualTo(answered);
        assertThat(reconcile(SupportQuestionStatus.AI_ANSWERED)).isEqualTo(answered);
        verify(repository).updateStatus(question, SupportQuestionStatus.UNANSWERED, SupportQuestionStatus.AI_ANSWERED);
        verify(notifications).notifySupportQuestionAnswered(any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SupportQuestionStatus.class, names = "UNANSWERED", mode = EnumSource.Mode.EXCLUDE)
    void delayedOutcomePreservesEveryRecordedOutcome(SupportQuestionStatus recorded) {
        authorize(true);
        var current = question(recorded, author);
        when(repository.findByIdAndChannelId(channel, question)).thenReturn(Optional.of(current));
        assertThat(reconcile(SupportQuestionStatus.AI_ANSWERED)).isEqualTo(current);
        verify(repository, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(notifications);
    }

    @Test
    void humanAnswerWinningConcurrentUpdateIsPreserved() {
        authorize(true);
        var human = question(SupportQuestionStatus.HUMAN_ANSWERED, author);
        when(repository.findByIdAndChannelId(channel, question))
                .thenReturn(Optional.of(question(SupportQuestionStatus.UNANSWERED, author)), Optional.of(human));
        when(repository.updateStatus(question, SupportQuestionStatus.UNANSWERED, SupportQuestionStatus.AI_ANSWERED)).thenReturn(false);
        assertThat(reconcile(SupportQuestionStatus.AI_ANSWERED)).isEqualTo(human);
        verifyNoInteractions(notifications);
    }

    @Test
    void revocationStillDeniesEvenAnAlreadyRecordedOutcome() {
        authorize(false);
        assertThatThrownBy(() -> reconcile(SupportQuestionStatus.AI_ANSWERED))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(repository, notifications);
    }

    @Test
    void anotherAuthorCannotReconcileAQuestion() {
        authorize(true);
        when(repository.findByIdAndChannelId(channel, question)).thenReturn(Optional.of(question(SupportQuestionStatus.UNANSWERED, UUID.randomUUID())));
        assertThatThrownBy(() -> reconcile(SupportQuestionStatus.AI_ANSWERED))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(403));
        verify(repository, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(notifications);
    }

    @Test
    void reconciliationCannotRequestHumanOrModerationStatus() {
        authorize(true);
        when(repository.findByIdAndChannelId(channel, question)).thenReturn(Optional.of(question(SupportQuestionStatus.CANCELLED, author)));
        assertThatThrownBy(() -> reconcile(SupportQuestionStatus.HUMAN_ANSWERED))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(400));
        verify(repository, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(notifications);
    }

    private SupportQuestion reconcile(SupportQuestionStatus status) {
        return service.reconcileAcceptedAnswerStatus(channel, question, author, status);
    }
    private void authorize(boolean enrolled) {
        when(access.requireAccess(channel, author)).thenReturn(new CourseChannelAccess(channel, UUID.randomUUID(), "Questions", enrolled, false));
    }
    private SupportQuestion question(SupportQuestionStatus status, UUID sender) {
        return new SupportQuestion(question, UUID.randomUUID(), channel, sender, "Question", status, "fixture", Instant.now());
    }
}
