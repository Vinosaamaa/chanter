import { useMemo, useState, type FormEvent } from 'react'
import { ExternalLink, FileText, Info, Plus, Send, Smile, Sparkles, ThumbsUp } from 'lucide-react'

import { useQuestionsChannel } from '../../../shell/hooks/use-questions-channel'
import type { QuestionsTimelineEntry } from '../../../shell/hooks/use-questions-channel'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

const demoQuestions = [
  { id: 'recursion', author: 'Sam', age: '1h ago', body: 'How do I trace recursion on merge sort?', tone: 'amber' as const, open: true },
  { id: 'big-o', author: 'Jordan', age: '3h ago', body: 'Big-O for nested loops?', tone: 'purple' as const, open: true },
  { id: 'office-hours', author: 'Priya', age: 'yesterday', body: 'When is office hours?', tone: 'green' as const, open: false },
]

const demoAnswer = {
  body: 'To trace recursion on merge sort, identify the base case and recursive case. The base case is when the subarray has length ≤ 1, which returns immediately. Otherwise, split the array into two halves, recursively sort each half, then merge them back together. Tracing this process as a recursion tree helps visualize the call stack and the combine steps.',
  sources: [
    { resourceId: 'lecture-2', resourceTitle: 'Lecture 2 — Recursion Deep Dive', excerpt: 'Covers merge sort recursion, base case, call stack visualization, and merge step.' },
    { resourceId: 'faq-recursion', resourceTitle: 'Approved FAQ — Tracing Recursion', excerpt: 'Step-by-step guide for tracing recursion with examples and diagrams.' },
  ],
}

export function CourseQuestionsPage() {
  const { course, courseCapabilities, selectedCohort } = useV2CourseWorkspace()
  const manageQuestions = courseCapabilities.canManageQuestions
  const questionChannel = course.channels.find((channel) => channel.name.toLowerCase() === 'questions')
  const questions = useQuestionsChannel({
    channelId: questionChannel?.id ?? 'questions-demo',
    cohortId: selectedCohort?.id ?? 'cohort-demo',
  })
  const [filter, setFilter] = useState<'open' | 'answered' | 'mine'>('open')
  const [selectedId, setSelectedId] = useState('recursion')
  const [draft, setDraft] = useState('')
  const [helpful, setHelpful] = useState(false)
  const [pickedQueueIds, setPickedQueueIds] = useState<string[]>([])

  const liveQuestions = useMemo(
    () => questions.timeline.filter((entry): entry is Extract<QuestionsTimelineEntry, { kind: 'learner-question' }> => entry.kind === 'learner-question'),
    [questions.timeline],
  )
  const selectedLive = selectedId.startsWith('live:')
    ? liveQuestions.find((entry) => `live:${entry.supportQuestion?.id ?? entry.message.id}` === selectedId)
    : undefined
  const selectedDemo = demoQuestions.find((question) => question.id === selectedId) ?? demoQuestions[0]
  const selectedAnswer = selectedLive ? questions.selectedAnswer : null

  const selectLiveQuestion = (entry: Extract<QuestionsTimelineEntry, { kind: 'learner-question' }>) => {
    const supportQuestionId = entry.supportQuestion?.id
    setSelectedId(`live:${supportQuestionId ?? entry.message.id}`)
    questions.selectSupportQuestion(supportQuestionId ?? null)
  }

  const submitQuestion = (event: FormEvent) => {
    event.preventDefault()
    void questions.postQuestion(draft).then((posted) => {
      if (posted) setDraft('')
    })
  }

  return (
    <div className={`questions-layout ${manageQuestions ? 'owner-view' : ''}`}>
      <aside className="questions-list-pane">
        <h2>QUESTIONS</h2>
        <div className="question-filters" role="group" aria-label="Question filters">
          {(['open', 'answered', 'mine'] as const).map((value) => (
            <button type="button" className={filter === value ? 'active' : undefined} onClick={() => setFilter(value)} key={value}>{value[0].toUpperCase() + value.slice(1)}</button>
          ))}
        </div>
        <div className="question-thread-list">
          {demoQuestions.filter((question) => filter !== 'answered' || !question.open).map((question) => (
            <button type="button" className={selectedId === question.id ? 'active' : undefined} key={question.id} onClick={() => setSelectedId(question.id)}>
              <V2Avatar name={question.author} tone={question.tone} size="md" />
              <span><strong>{question.body}</strong><small>{question.author} · {question.age}</small></span>
              <i className={question.open ? '' : 'answered'} />
            </button>
          ))}
          {liveQuestions.map((entry) => {
            const id = `live:${entry.supportQuestion?.id ?? entry.message.id}`
            return (
              <button type="button" className={selectedId === id ? 'active' : undefined} key={id} onClick={() => selectLiveQuestion(entry)}>
                <V2Avatar name="You" tone="blue" size="md" />
                <span><strong>{entry.message.body}</strong><small>You · just now</small></span>
                <i />
              </button>
            )
          })}
        </div>
      </aside>

      <section className="question-detail-pane">
        {manageQuestions ? <h2 className="question-pane-label">THREAD</h2> : null}
        <article className="question-message">
          <V2Avatar name={selectedLive ? 'You' : selectedDemo.author} tone={selectedLive ? 'blue' : selectedDemo.tone} size="lg" />
          <div><p><strong>{selectedLive ? 'You' : selectedDemo.author}</strong><time>{selectedLive ? 'just now' : selectedDemo.age}</time></p><span>{selectedLive?.message.body ?? selectedDemo.body}</span></div>
        </article>
        <div className="question-answer-divider" />
        <article className="assistant-answer">
          <span className="assistant-avatar"><Sparkles /></span>
          <div>
            <p><strong>AI Study Assistant</strong><b>AI</b><time>1h ago</time></p>
            <span>{selectedAnswer?.answerBody ?? demoAnswer.body}</span>
            <small>Sources</small>
            <div className="answer-sources">
              {(selectedAnswer?.sources ?? demoAnswer.sources).map((source, index) => (
                <article key={source.resourceId}>
                  <span className={index ? 'faq-source-icon' : 'document-source-icon'}>{index ? 'FAQ' : <FileText />}</span>
                  <div><strong>{source.resourceTitle}</strong><p>{source.excerpt}</p></div>
                  <button type="button">View <ExternalLink /></button>
                </article>
              ))}
            </div>
          </div>
        </article>
        <div className="question-answer-actions">
          {selectedLive?.supportQuestion && !selectedAnswer ? (
            <button type="button" onClick={() => void questions.invokeAssistant(selectedLive.supportQuestion!.id)} disabled={questions.invokingQuestionId === selectedLive.supportQuestion.id}><Sparkles />{questions.invokingQuestionId ? 'Asking AI…' : 'Ask AI'}</button>
          ) : null}
          <button type="button" onClick={() => {
            if (selectedLive?.supportQuestion && selectedAnswer?.handoffRecommended) void questions.addToTaQueue(selectedLive.supportQuestion.id)
          }}><Plus />Add to TA Queue</button>
          {!manageQuestions ? <button type="button" className={helpful ? 'active' : undefined} onClick={() => setHelpful((value) => !value)}><ThumbsUp />{helpful ? 'Helpful' : 'Mark helpful'}</button> : null}
        </div>
        {questions.error && questionChannel ? <p className="inline-error">{questions.error}</p> : null}
        {questions.taQueueSuccess ? <p className="inline-success">{questions.taQueueSuccess}</p> : null}
        <form className="question-composer" onSubmit={submitQuestion}>
          <button type="button" aria-label="Add attachment"><Plus /></button>
          <input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={selectedLive || manageQuestions ? 'Ask a follow-up…' : 'Ask a support question…'} />
          <button type="button" aria-label="Add emoji"><Smile /></button>
          <button type="submit" className="send-button" aria-label="Send question" disabled={!draft.trim() || questions.isPosting}><Send /></button>
        </form>
      </section>

      {manageQuestions ? (
        <aside className="question-owner-tools">
          {courseCapabilities.canApproveFaq ? <section><h2>FAQ CANDIDATES <Info /></h2>{[['12','Tracing Recursion'],['8','Merge Sort Steps'],['7','Recursion Base Case'],['5','Recursion Tree']].map(([count,label]) => <p key={label}><b>{count}</b><span>{label}</span></p>)}<button type="button" className="owner-tool-link">View all</button></section> : null}
          {courseCapabilities.canManageTaQueue ? <section><h2>TA QUEUE <Info /></h2>{demoQuestions.map((question,index) => <p key={question.id}><b>{3-index}</b><span><strong>{question.body}</strong><small>{question.author} · {question.age}</small></span><button type="button" disabled={pickedQueueIds.includes(question.id)} onClick={() => setPickedQueueIds((items) => [...items,question.id])}>{pickedQueueIds.includes(question.id) ? 'Picked up' : 'Pick up'}</button></p>)}<button type="button" className="owner-tool-link">View queue</button></section> : null}
        </aside>
      ) : null}
    </div>
  )
}
