import { cn } from '../../../lib/cn'
import { useFaqApprovalPanel } from '../hooks/use-faq-approval-panel'

type FaqApprovalPanelProps = {
  courseTitle: string
  courseId: string
  questionsChannelId: string | undefined
}

export function FaqApprovalPanel({
  courseTitle,
  courseId,
  questionsChannelId,
}: FaqApprovalPanelProps) {
  const faq = useFaqApprovalPanel(courseId, questionsChannelId)

  const selectedCandidate = faq.candidates[faq.selectedIndex]

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <header className="border-b border-app-border px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Support operations
        </p>
        <h2 className="mt-1 text-xl font-semibold text-app-text">FAQ approval</h2>
        <p className="mt-1 text-sm text-app-muted">{courseTitle}</p>
      </header>

      <div className="flex min-h-0 flex-1 flex-col lg:flex-row">
        <div className="min-w-0 flex-1 overflow-y-auto border-b border-app-border p-4 lg:border-b-0 lg:border-r">
          {!questionsChannelId && (
            <p className="text-sm text-app-muted">
              This course does not have a #questions channel yet.
            </p>
          )}

          {faq.isLoading && <p className="text-sm text-app-muted">Loading FAQ candidates…</p>}

          {faq.accessDenied && (
            <p className="rounded-lg border border-app-border bg-app-surface px-4 py-3 text-sm text-app-muted">
              Only course instructors can review and approve FAQ candidates.
            </p>
          )}

          {faq.error && (
            <p
              role="status"
              aria-live="polite"
              className="mb-3 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200"
            >
              {faq.error}
            </p>
          )}

          {faq.actionMessage && (
            <p
              role="status"
              aria-live="polite"
              className="mb-3 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200"
            >
              {faq.actionMessage}
            </p>
          )}

          {!faq.isLoading && !faq.accessDenied && questionsChannelId && (
            <>
              <div className="mb-4 flex items-center justify-between gap-3">
                <p className="text-sm text-app-muted">
                  {faq.candidates.length === 0
                    ? 'No repeated-question groups detected.'
                    : `${faq.candidates.length} candidate group${faq.candidates.length === 1 ? '' : 's'}.`}
                </p>
                <button
                  type="button"
                  disabled={faq.isSaving}
                  onClick={() => void faq.refresh()}
                  className="rounded-lg border border-app-border px-3 py-1.5 text-xs text-app-muted hover:bg-app-elevated hover:text-app-text"
                >
                  Refresh
                </button>
              </div>

              <ul className="flex flex-col gap-3">
                {faq.candidates.map((candidate, index) => (
                  <li key={`${candidate.representativeQuestion}:${index}`}>
                    <button
                      type="button"
                      disabled={faq.isSaving}
                      onClick={() => faq.selectCandidate(index)}
                      className={cn(
                        'w-full rounded-xl border px-4 py-3 text-left transition-colors',
                        faq.selectedIndex === index && faq.editingFaqId === null
                          ? 'border-app-accent bg-app-accent/10'
                          : 'border-app-border bg-app-surface hover:bg-app-elevated/70',
                      )}
                    >
                      <p className="text-sm font-medium text-app-text">
                        {candidate.representativeQuestion}
                      </p>
                      <p className="mt-1 text-xs text-app-muted">
                        {candidate.supportQuestions.length} linked question
                        {candidate.supportQuestions.length === 1 ? '' : 's'}
                      </p>
                    </button>
                  </li>
                ))}
              </ul>

              {faq.approvedFaqs.length > 0 && (
                <div className="mt-6">
                  <h3 className="text-sm font-semibold text-app-text">Approved FAQs</h3>
                  <ul className="mt-3 flex flex-col gap-2">
                    {faq.approvedFaqs.map((approved) => (
                      <li
                        key={approved.id}
                        className="flex items-center justify-between gap-3 rounded-lg border border-app-border px-3 py-2"
                      >
                        <span className="truncate text-sm text-app-text">{approved.question}</span>
                        <button
                          type="button"
                          disabled={faq.isSaving}
                          onClick={() => faq.startEditApproved(approved)}
                          className="shrink-0 rounded-md border border-app-border px-2 py-1 text-xs text-app-muted hover:text-app-text"
                        >
                          Edit
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}
        </div>

        <aside className="w-full shrink-0 overflow-y-auto p-4 lg:w-96">
          <h3 className="text-sm font-semibold text-app-text">
            {faq.editingFaqId ? 'Edit approved FAQ' : 'Preview & approve'}
          </h3>
          <p className="mt-1 text-xs text-app-muted">
            Approved FAQs are searchable and used by the AI Study Assistant.
          </p>

          <label htmlFor="faq-question-draft" className="mt-4 block text-xs font-medium text-app-muted">
            Question
          </label>
          <textarea
            id="faq-question-draft"
            value={faq.questionDraft}
            onChange={(event) => faq.setQuestionDraft(event.target.value)}
            rows={3}
            disabled={!questionsChannelId || faq.accessDenied || faq.isSaving}
            className="mt-1 w-full rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text outline-none ring-app-accent focus:ring-2 disabled:opacity-60"
          />

          <label htmlFor="faq-answer-draft" className="mt-4 block text-xs font-medium text-app-muted">
            Answer
          </label>
          <textarea
            id="faq-answer-draft"
            value={faq.answerDraft}
            onChange={(event) => faq.setAnswerDraft(event.target.value)}
            rows={8}
            placeholder="Write the instructor-approved answer learners and the assistant should use."
            disabled={!questionsChannelId || faq.accessDenied || faq.isSaving}
            className="mt-1 w-full rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text outline-none ring-app-accent focus:ring-2 disabled:opacity-60"
          />

          {selectedCandidate && faq.editingFaqId === null && (
            <p className="mt-3 text-xs text-app-muted">
              Sources: {selectedCandidate.supportQuestions.length} support question
              {selectedCandidate.supportQuestions.length === 1 ? '' : 's'} in #questions.
            </p>
          )}

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={!questionsChannelId || faq.accessDenied || faq.isSaving}
              onClick={() => void faq.approveOrUpdate()}
              className="rounded-lg bg-app-accent px-3 py-2 text-sm font-medium text-white disabled:opacity-60"
            >
              {faq.editingFaqId ? 'Save changes' : 'Approve FAQ'}
            </button>
            {faq.editingFaqId && (
              <button
                type="button"
                disabled={faq.isSaving}
                onClick={faq.clearEdit}
                className="rounded-lg border border-app-border px-3 py-2 text-sm text-app-muted hover:text-app-text"
              >
                Cancel edit
              </button>
            )}
          </div>
        </aside>
      </div>
    </section>
  )
}
