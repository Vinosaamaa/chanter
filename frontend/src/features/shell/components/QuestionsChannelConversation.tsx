import { type FormEvent, useEffect, useState, type ReactNode } from 'react'

import { useAuthStore } from '../../../stores/auth-store'
import { useQuestionsPanel } from '../context/use-questions-panel'
import { useQuestionsChannel } from '../hooks/use-questions-channel'
import { useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import type { CourseChannelContext } from '../shell-routes'
import { findCourseChannelContext } from '../shell-routes'

type QuestionsChannelConversationProps = {
  serverId: string
  channelId: string
  channelContext: CourseChannelContext
}

export function QuestionsChannelConversation({
  serverId,
  channelId,
  channelContext,
}: QuestionsChannelConversationProps) {
  const currentUserId = useAuthStore((state) => state.user?.id)
  const cohortId = channelContext.course.cohorts[0]?.id
  const { setStudyServerId, setSelectedAnswer } = useQuestionsPanel()
  const questions = useQuestionsChannel({
    channelId,
    cohortId: cohortId ?? '',
  })
  const [draft, setDraft] = useState('')

  useEffect(() => {
    setStudyServerId(serverId)
    return () => {
      setStudyServerId(null)
      setSelectedAnswer(null)
    }
  }, [serverId, setSelectedAnswer, setStudyServerId])

  useEffect(() => {
    setSelectedAnswer(questions.selectedAnswer)
  }, [questions.selectedAnswer, setSelectedAnswer])

  if (!cohortId) {
    return (
      <ConversationFrame title="#questions unavailable">
        <p className="text-sm text-app-muted">This course has no cohort configured for TA queue handoff.</p>
      </ConversationFrame>
    )
  }

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void questions.postQuestion(draft).then((posted) => {
      if (posted) {
        setDraft('')
      }
    })
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <header className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Support questions
        </p>
        <h2 className="mt-1 text-base font-semibold text-app-text">#{channelContext.channel.name}</h2>
        <p className="mt-1 text-sm text-app-muted">
          Ask questions and get help from your peers and AI assistant.
        </p>
      </header>

      <div className="flex min-h-0 flex-1 flex-col">
        {questions.isLoadingHistory ? (
          <p className="p-4 text-sm text-app-muted">Loading question history…</p>
        ) : null}

        {questions.error ? (
          <p className="border-b border-rose-500/30 bg-rose-500/10 px-4 py-2 text-sm text-rose-200">
            {questions.error}
          </p>
        ) : null}

        {questions.taQueueSuccess ? (
          <p className="border-b border-emerald-500/30 bg-emerald-500/10 px-4 py-2 text-sm text-emerald-200">
            {questions.taQueueSuccess}
          </p>
        ) : null}

        <ol className="flex-1 space-y-4 overflow-y-auto px-4 py-4">
          {questions.timeline.length === 0 ? (
            <li className="text-sm text-app-muted">
              No support questions yet. Post your first question for the AI Study Assistant.
            </li>
          ) : (
            questions.timeline.map((entry) => {
              if (entry.kind === 'ai-answer') {
                return (
                  <li key={`answer-${entry.answer.id}`}>
                    <AiAnswerCard
                      answer={entry.answer}
                      isSelected={questions.selectedSupportQuestionId === entry.supportQuestionId}
                      onSelect={() => questions.selectSupportQuestion(entry.supportQuestionId)}
                      onAddToTaQueue={() => void questions.addToTaQueue(entry.supportQuestionId)}
                      isAddingToQueue={questions.addingToQueueQuestionId === entry.supportQuestionId}
                    />
                  </li>
                )
              }

              const isOwnQuestion = entry.message.senderUserId === currentUserId
              const supportQuestion = entry.supportQuestion
              const hasAnswer = supportQuestion
                ? questions.timeline.some(
                    (item) =>
                      item.kind === 'ai-answer' && item.supportQuestionId === supportQuestion.id,
                  )
                : false
              const canAskAi =
                isOwnQuestion &&
                supportQuestion?.status === 'UNANSWERED' &&
                !hasAnswer

              return (
                <li key={entry.message.id}>
                  <LearnerQuestionCard
                    body={entry.message.body}
                    createdAt={entry.message.createdAt}
                    isOwnQuestion={isOwnQuestion}
                    canAskAi={Boolean(canAskAi)}
                    isInvoking={questions.invokingQuestionId === supportQuestion?.id}
                    onAskAi={() => {
                      if (supportQuestion) {
                        void questions.invokeAssistant(supportQuestion.id)
                      }
                    }}
                    onSelect={() => {
                      if (supportQuestion) {
                        questions.selectSupportQuestion(supportQuestion.id)
                      }
                    }}
                  />
                </li>
              )
            })
          )}
        </ol>

        <form className="border-t border-app-border p-4" onSubmit={onSubmit}>
          <label className="sr-only" htmlFor="support-question-input">
            Message #questions
          </label>
          <div className="flex gap-2">
            <input
              id="support-question-input"
              className="min-w-0 flex-1 rounded-md border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text outline-none ring-app-accent focus:ring-2"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="Message #questions"
              disabled={questions.isPosting}
            />
            <button
              type="submit"
              className="rounded-md bg-app-accent px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
              disabled={questions.isPosting || draft.trim().length === 0}
            >
              {questions.isPosting ? 'Posting…' : 'Send'}
            </button>
          </div>
        </form>
      </div>
    </section>
  )
}

export function QuestionsChannelGate({
  serverId,
  channelId,
}: {
  serverId: string | undefined
  channelId: string
}) {
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const channelContext = findCourseChannelContext(navigationQuery.data, channelId)

  if (navigationQuery.isLoading) {
    return (
      <ConversationFrame title="Loading #questions…">
        <p className="text-sm text-app-muted">Checking your access to this channel.</p>
      </ConversationFrame>
    )
  }

  if (navigationQuery.isError || !channelContext || !serverId) {
    return (
      <ConversationFrame title="#questions unavailable">
        <p className="text-sm text-app-muted">You do not have access to this channel.</p>
      </ConversationFrame>
    )
  }

  return (
    <QuestionsChannelConversation
      key={channelId}
      serverId={serverId}
      channelId={channelId}
      channelContext={channelContext}
    />
  )
}

function LearnerQuestionCard({
  body,
  createdAt,
  isOwnQuestion,
  canAskAi,
  isInvoking,
  onAskAi,
  onSelect,
}: {
  body: string
  createdAt: string
  isOwnQuestion: boolean
  canAskAi: boolean
  isInvoking: boolean
  onAskAi: () => void
  onSelect: () => void
}) {
  return (
    <article
      className="rounded-lg border border-app-border bg-app-surface px-3 py-2"
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          onSelect()
        }
      }}
      role="button"
      tabIndex={0}
    >
      <div className="flex items-center justify-between gap-2 text-xs text-app-muted">
        <span>{isOwnQuestion ? 'You' : 'Learner'}</span>
        <time dateTime={createdAt}>{formatTimestamp(createdAt)}</time>
      </div>
      <p className="mt-1 whitespace-pre-wrap text-sm text-app-text">{body}</p>
      {canAskAi ? (
        <button
          type="button"
          className="mt-2 rounded-md border border-app-accent px-3 py-1 text-xs font-semibold text-app-accent hover:bg-app-accent/10"
          onClick={(event) => {
            event.stopPropagation()
            onAskAi()
          }}
          disabled={isInvoking}
        >
          {isInvoking ? 'Asking AI…' : 'Ask AI'}
        </button>
      ) : null}
    </article>
  )
}

function AiAnswerCard({
  answer,
  isSelected,
  onSelect,
  onAddToTaQueue,
  isAddingToQueue,
}: {
  answer: import('../../questions/support-question-types').AssistantAnswer
  isSelected: boolean
  onSelect: () => void
  onAddToTaQueue: () => void
  isAddingToQueue: boolean
}) {
  return (
    <article
      className={`rounded-lg border px-3 py-3 ${
        isSelected ? 'border-app-accent bg-app-accent/10' : 'border-app-accent/40 bg-app-surface'
      }`}
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          onSelect()
        }
      }}
      role="button"
      tabIndex={0}
    >
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.12em] text-app-accent">
        <span className="rounded bg-app-accent px-1.5 py-0.5 text-[10px] text-white">AI</span>
        <span>AI Study Assistant</span>
        <span className="ml-auto font-normal normal-case tracking-normal text-app-muted">
          {answer.confidence === 'LOW' ? 'Low confidence' : 'Grounded answer'}
        </span>
      </div>
      <p className="mt-2 whitespace-pre-wrap text-sm text-app-text">{answer.answerBody}</p>
      {answer.sources.length > 0 ? (
        <div className="mt-3">
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">Sources</p>
          <ul className="mt-2 space-y-2">
            {answer.sources.map((source) => (
              <li
                key={source.resourceId}
                className="rounded-md border border-app-border bg-app-bg px-2 py-2 text-xs"
              >
                <p className="font-medium text-app-text">{source.resourceTitle}</p>
                <p className="mt-1 text-app-muted">{source.excerpt}</p>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        {answer.handoffRecommended ? (
          <button
            type="button"
            className="rounded-md bg-app-accent px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-60"
            onClick={(event) => {
              event.stopPropagation()
              onAddToTaQueue()
            }}
            disabled={isAddingToQueue}
          >
            {isAddingToQueue ? 'Adding…' : '+ Add to TA Queue'}
          </button>
        ) : null}
        <button
          type="button"
          className="rounded-md border border-app-border px-3 py-1.5 text-xs font-semibold text-app-muted"
          onClick={(event) => event.stopPropagation()}
        >
          Mark helpful
        </button>
      </div>
    </article>
  )
}

function ConversationFrame({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <div className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Support questions
        </p>
        <h2 className="mt-1 text-base font-semibold text-app-text">{title}</h2>
      </div>
      <div className="flex flex-1 items-center justify-center p-6">{children}</div>
    </section>
  )
}

function formatTimestamp(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}
