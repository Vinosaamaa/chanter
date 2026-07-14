import { useMemo, useRef, useState, type FormEvent } from 'react'
import {
  CheckCircle2,
  Copy,
  ExternalLink,
  FileText,
  Info,
  Plus,
  RefreshCw,
  Send,
  Sparkles,
  ThumbsUp,
  XCircle,
} from 'lucide-react'

import { useAuthStore } from '../../../../stores/auth-store'
import type { PublicUserProfile } from '../../../friends/types'
import type { SupportQuestionStatus } from '../../../questions/support-question-types'
import { useFaqApprovalPanel } from '../../../support-operations/hooks/use-faq-approval-panel'
import { useTaQueuePanel } from '../../../support-operations/hooks/use-ta-queue-panel'
import { useQuestionsChannel } from '../../../shell/hooks/use-questions-channel'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

type QuestionFilter = 'open' | 'answered' | 'mine'

const OPEN_STATUSES: SupportQuestionStatus[] = ['UNANSWERED', 'AI_LOW_CONFIDENCE']

function displayName(userId: string, profiles: Record<string, PublicUserProfile>): string {
  return profiles[userId]?.displayName ?? `Member ${userId.slice(0, 8)}`
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value))
}

function statusLabel(status: SupportQuestionStatus): string {
  switch (status) {
    case 'UNANSWERED': return 'Open'
    case 'AI_ANSWERED': return 'AI answered'
    case 'AI_LOW_CONFIDENCE': return 'Needs human help'
    case 'HUMAN_ANSWERED': return 'Staff answered'
    case 'RESOLVED': return 'Resolved'
    case 'CANCELLED': return 'Cancelled'
    case 'DUPLICATE': return 'Duplicate'
  }
}

function questionsForFilter(
  supportQuestions: ReturnType<typeof useQuestionsChannel>['supportQuestions'],
  filter: QuestionFilter,
  userId: string | null,
) {
  const sorted = [...supportQuestions].sort((left, right) =>
    right.createdAt.localeCompare(left.createdAt),
  )
  if (filter === 'mine') return sorted.filter((question) => question.senderUserId === userId)
  if (filter === 'open') return sorted.filter((question) => OPEN_STATUSES.includes(question.status))
  return sorted.filter((question) => !OPEN_STATUSES.includes(question.status))
}

export function CourseQuestionsPage() {
  const { serverId, course, courseCapabilities, selectedCohort } = useV2CourseWorkspace()
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const manageQuestions = courseCapabilities.canManageQuestions
  const questionChannel = course.channels.find((channel) => channel.name.toLowerCase() === 'questions')
  const questions = useQuestionsChannel({
    channelId: questionChannel?.id ?? '',
    cohortId: selectedCohort?.id ?? '',
  })
  const faq = useFaqApprovalPanel(course.id, questionChannel?.id)
  const queue = useTaQueuePanel(selectedCohort?.id)
  const [filter, setFilter] = useState<QuestionFilter>('open')
  const [draft, setDraft] = useState('')
  const draftVersionRef = useRef(0)
  const selected = questions.selectedQuestion

  const updateDraft = (value: string) => {
    draftVersionRef.current += 1
    setDraft(value)
  }

  const clearDraft = () => {
    draftVersionRef.current += 1
    setDraft('')
  }

  const requestedQuestions = useMemo(
    () => questionsForFilter(questions.supportQuestions, filter, userId),
    [filter, questions.supportQuestions, userId],
  )
  const activeFilter = selected && filter !== 'mine' &&
      !requestedQuestions.some((question) => question.id === selected.id)
    ? (OPEN_STATUSES.includes(selected.status) ? 'open' : 'answered')
    : filter
  const filteredQuestions = useMemo(
    () => activeFilter === filter
      ? requestedQuestions
      : questionsForFilter(questions.supportQuestions, activeFilter, userId),
    [activeFilter, filter, questions.supportQuestions, requestedQuestions, userId],
  )

  const changeFilter = (nextFilter: QuestionFilter) => {
    clearDraft()
    setFilter(nextFilter)
    questions.selectSupportQuestion(
      questionsForFilter(questions.supportQuestions, nextFilter, userId)[0]?.id ?? null,
    )
  }

  const selectedAuthor = selected
    ? displayName(selected.senderUserId, questions.profilesById)
    : null
  const selectedCandidate = faq.candidates[faq.selectedIndex]
  const canAskAi = Boolean(
    selected && selected.senderUserId === userId && selected.status === 'UNANSWERED',
  )
  const canModerate = Boolean(
    selected && !['RESOLVED', 'CANCELLED', 'DUPLICATE'].includes(selected.status),
  )

  const submitComposer = (event: FormEvent) => {
    event.preventDefault()
    if (!selected && manageQuestions) return
    const submittedDraftVersion = draftVersionRef.current
    const action = manageQuestions && selected
      ? questions.postReply(selected.id, draft)
      : questions.postQuestion(draft)
    void action.then((saved) => {
      if (saved && draftVersionRef.current === submittedDraftVersion) clearDraft()
    })
  }

  if (!questionChannel) {
    return <section className="course-workspace-state" role="status"><p>This course has no #questions channel.</p></section>
  }

  return (
    <div className={`questions-layout ${manageQuestions ? 'owner-view' : ''}`}>
      <aside className="questions-list-pane">
        <div className="question-list-heading">
          <h2>QUESTIONS</h2>
          <button type="button" aria-label="Refresh questions" onClick={() => void questions.refresh()}>
            <RefreshCw />
          </button>
        </div>
        <div className="question-filters" role="group" aria-label="Question filters">
          {(['open', 'answered', 'mine'] as const).map((value) => (
            <button
              type="button"
              className={activeFilter === value ? 'active' : undefined}
              onClick={() => changeFilter(value)}
              key={value}
            >
              {value[0].toUpperCase() + value.slice(1)}
            </button>
          ))}
        </div>
        <div className="question-thread-list">
          {questions.isLoadingHistory ? <p className="question-empty-state">Loading questions…</p> : null}
          {!questions.isLoadingHistory && filteredQuestions.length === 0 ? (
            <p className="question-empty-state">No {activeFilter} questions.</p>
          ) : null}
          {filteredQuestions.map((question) => {
            const author = displayName(question.senderUserId, questions.profilesById)
            return (
              <button
                type="button"
                className={questions.selectedSupportQuestionId === question.id ? 'active' : undefined}
                key={question.id}
                onClick={() => {
                  clearDraft()
                  questions.selectSupportQuestion(question.id)
                }}
              >
                <V2Avatar name={author} tone="blue" size="md" />
                <span>
                  <strong>{question.body}</strong>
                  <small>{author} · {formatTime(question.createdAt)} · {statusLabel(question.status)}</small>
                </span>
                <i className={OPEN_STATUSES.includes(question.status) ? '' : 'answered'} />
              </button>
            )
          })}
        </div>
      </aside>

      <section className="question-detail-pane">
        {manageQuestions ? <h2 className="question-pane-label">THREAD</h2> : null}
        {!selected ? (
          <div className="question-detail-empty">
            <h2>{questions.supportQuestions.length === 0 ? 'No questions yet' : 'Select a question'}</h2>
            <p>{manageQuestions ? 'Choose a learner question to reply or moderate.' : 'Ask a support question below.'}</p>
          </div>
        ) : (
          <>
            <article className="question-message">
              <V2Avatar name={selectedAuthor ?? 'Member'} tone="blue" size="lg" />
              <div>
                <p><strong>{selectedAuthor}</strong><time>{formatTime(selected.createdAt)}</time></p>
                <span>{selected.body}</span>
                <small className="question-status-label">{statusLabel(selected.status)}</small>
              </div>
            </article>

            {questions.selectedAnswer ? (
              <>
                <div className="question-answer-divider" />
                <article className="assistant-answer">
                  <span className="assistant-avatar"><Sparkles /></span>
                  <div>
                    <p><strong>AI Study Assistant</strong><b>AI</b><time>{formatTime(questions.selectedAnswer.createdAt)}</time></p>
                    <span>{questions.selectedAnswer.answerBody}</span>
                    <small>Sources</small>
                    <div className="answer-sources">
                      {questions.selectedAnswer.sources.map((source) => {
                        const isFaqSource = source.resourceTitle.startsWith('FAQ: ')
                        return (
                          <article key={source.resourceId}>
                            <span className={isFaqSource ? 'faq-source-icon' : 'document-source-icon'}>
                              {isFaqSource ? 'FAQ' : <FileText />}
                            </span>
                            <div><strong>{source.resourceTitle}</strong><p>{source.excerpt}</p></div>
                            {!isFaqSource ? (
                              <a href={`/app/servers/${serverId}/courses/${course.id}/resources?resource=${source.resourceId}`}>
                                View <ExternalLink />
                              </a>
                            ) : null}
                          </article>
                        )
                      })}
                    </div>
                  </div>
                </article>
              </>
            ) : null}

            {questions.selectedReplies.map((reply) => {
              const author = displayName(reply.authorUserId, questions.profilesById)
              return (
                <article className="question-message question-staff-reply" key={reply.id}>
                  <V2Avatar name={author} tone="green" size="lg" />
                  <div>
                    <p><strong>{author}</strong><b>STAFF</b><time>{formatTime(reply.createdAt)}</time></p>
                    <span>{reply.body}</span>
                  </div>
                </article>
              )
            })}

            <div className="question-answer-actions">
              {canAskAi ? (
                <button
                  type="button"
                  onClick={() => void questions.invokeAssistant(selected.id)}
                  disabled={questions.invokingQuestionId === selected.id}
                >
                  <Sparkles />{questions.invokingQuestionId ? 'Asking AI…' : 'Ask AI'}
                </button>
              ) : null}
              {!manageQuestions
                  && selected?.senderUserId === userId
                  && questions.selectedAnswer?.handoffRecommended ? (
                <button
                  type="button"
                  onClick={() => void questions.addToTaQueue(selected.id)}
                  disabled={questions.addingToQueueQuestionId === selected.id}
                >
                  <Plus />{questions.addingToQueueQuestionId ? 'Adding…' : 'Add to TA Queue'}
                </button>
              ) : null}
              {!manageQuestions && questions.selectedAnswer ? (
                <button type="button" disabled title="Helpful voting will ship with the streaming AI audit trail in issue #100.">
                  <ThumbsUp />Mark helpful
                </button>
              ) : null}
              {manageQuestions && canModerate ? (
                <>
                  <button type="button" disabled={questions.isModerating} onClick={() => void questions.moderateQuestion(selected.id, 'RESOLVED')}><CheckCircle2 />Resolve</button>
                  <button type="button" disabled={questions.isModerating} onClick={() => void questions.moderateQuestion(selected.id, 'DUPLICATE')}><Copy />Duplicate</button>
                  <button type="button" disabled={questions.isModerating} onClick={() => void questions.moderateQuestion(selected.id, 'CANCELLED')}><XCircle />Cancel</button>
                </>
              ) : null}
            </div>
          </>
        )}

        {questions.error ? <p className="inline-error">{questions.error}</p> : null}
        {questions.taQueueSuccess ? <p className="inline-success">{questions.taQueueSuccess}</p> : null}
        {(!manageQuestions || selected) ? (
          <form className="question-composer" onSubmit={submitComposer}>
            <input
              value={draft}
              onChange={(event) => updateDraft(event.target.value)}
              placeholder={manageQuestions ? 'Reply to this question…' : 'Ask a support question…'}
            />
            <button
              type="submit"
              className="send-button"
              aria-label={manageQuestions ? 'Send reply' : 'Send question'}
              disabled={!draft.trim() || questions.isPosting || questions.isPostingReply}
            >
              <Send />
            </button>
          </form>
        ) : null}
      </section>

      {manageQuestions ? (
        <aside className="question-owner-tools">
          {courseCapabilities.canApproveFaq ? (
            <section>
              <div className="owner-tool-heading">
                <h2>FAQ CANDIDATES <Info /></h2>
                <button type="button" aria-label="Refresh FAQ candidates" onClick={() => void faq.refresh()} disabled={faq.isSaving}><RefreshCw /></button>
              </div>
              {faq.isLoading ? <small>Loading candidates…</small> : null}
              {!faq.isLoading && faq.candidates.length === 0 ? <small>No repeated-question groups.</small> : null}
              {faq.candidates.slice(0, 4).map((candidate, index) => (
                <button
                  type="button"
                  className={`owner-tool-candidate ${faq.selectedIndex === index ? 'active' : ''}`}
                  aria-pressed={faq.selectedIndex === index}
                  key={`${candidate.representativeQuestion}:${index}`}
                  onClick={() => faq.selectCandidate(index)}
                >
                  <b>{candidate.supportQuestions.length}</b><span>{candidate.representativeQuestion}</span>
                </button>
              ))}
              {selectedCandidate ? (
                <div className="owner-tool-faq-form">
                  <textarea aria-label="Approved FAQ answer" value={faq.answerDraft} onChange={(event) => faq.setAnswerDraft(event.target.value)} placeholder="Write the approved answer…" />
                  <button type="button" disabled={faq.isSaving || !faq.answerDraft.trim()} onClick={() => void faq.approveOrUpdate()}>{faq.isSaving ? 'Saving…' : 'Approve FAQ'}</button>
                </div>
              ) : null}
              {faq.actionMessage ? <small className="inline-success">{faq.actionMessage}</small> : null}
              {faq.error ? <small className="inline-error">{faq.error}</small> : null}
            </section>
          ) : null}

          {courseCapabilities.canManageTaQueue ? (
            <section>
              <div className="owner-tool-heading">
                <h2>TA QUEUE <Info /></h2>
                <button type="button" aria-label="Refresh TA queue" onClick={() => void queue.refresh()}><RefreshCw /></button>
              </div>
              {queue.isLoading ? <small>Loading queue…</small> : null}
              {!queue.isLoading && queue.items.length === 0 ? <small>No learners waiting.</small> : null}
              {queue.items.slice(0, 4).map((item, index) => (
                <p key={item.id}>
                  <b>{index + 1}</b>
                  <span>
                    <strong>{item.body}</strong>
                    <small>{displayName(item.learnerUserId, queue.profilesById)} · {formatTime(item.createdAt)}</small>
                  </span>
                  <button
                    type="button"
                    aria-label={`${item.status === 'OPEN' ? 'Pick up' : 'Resolve'} TA queue question: ${item.body}`}
                    disabled={queue.actingItemId === item.id}
                    onClick={() => {
                      const linkedQuestion = questions.supportQuestions.find(
                        (question) => question.id === item.supportQuestionId,
                      )
                      if (linkedQuestion) {
                        setFilter(OPEN_STATUSES.includes(linkedQuestion.status) ? 'open' : 'answered')
                      }
                      clearDraft()
                      questions.selectSupportQuestion(item.supportQuestionId)
                      if (item.status === 'OPEN') {
                        void queue.pickupItem(item.id)
                        return
                      }
                      void queue.resolveItem(item.id).then(() => questions.refresh())
                    }}
                  >
                    {item.status === 'OPEN' ? 'Pick up' : 'Resolve'}
                  </button>
                </p>
              ))}
              {queue.actionMessage ? <small className="inline-success">{queue.actionMessage}</small> : null}
              {queue.error ? <small className="inline-error">{queue.error}</small> : null}
            </section>
          ) : null}
        </aside>
      ) : null}
    </div>
  )
}
