import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

import { cn } from '../../../lib/cn'

import { useInstructorDashboardPage } from '../hooks/use-instructor-dashboard-page'
import {
  SAAS_PLAN_LABELS,
  SAAS_PLAN_TIERS,
  type SaasPlanTier,
} from '../instructor-dashboard-types'

type MetricCardProps = {
  label: string
  value: string | number
  hint?: string
}

function MetricCard({ label, value, hint }: MetricCardProps) {
  return (
    <article className="rounded-xl border border-app-border bg-app-surface p-4">
      <p className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-app-text">{value}</p>
      {hint ? <p className="mt-1 text-xs text-app-muted">{hint}</p> : null}
    </article>
  )
}

function formatPercent(used: number, limit: number): string {
  if (limit <= 0) {
    return '0%'
  }
  return `${Math.min(100, Math.round((used / limit) * 100))}%`
}

export function InstructorDashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedServerId = searchParams.get('serverId')

  const setSelectedServerId = useMemo(
    () => (serverId: string) => {
      setSearchParams({ serverId }, { replace: true })
    },
    [setSearchParams],
  )

  const page = useInstructorDashboardPage(selectedServerId, setSelectedServerId)
  const selectedServerName =
    page.servers.find((server) => server.id === page.selectedServerId)?.name ?? 'Study Server'

  const aiUsagePercent = page.dashboard
    ? formatPercent(page.dashboard.aiInvocationCount, page.dashboard.aiInvocationLimit)
    : '0%'

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-app-bg">
      <header className="border-b border-app-border px-6 py-5">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Instructor operations
        </p>
        <div className="mt-2 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 className="text-2xl font-semibold text-app-text">Instructor Dashboard</h1>
            <p className="mt-1 text-sm text-app-muted">
              Live support load, Office Hours activity, and AI usage for your Study Server.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {page.servers.length > 1 ? (
              <label className="flex flex-col gap-1 text-xs text-app-muted">
                Study Server
                <select
                  value={page.selectedServerId ?? ''}
                  onChange={(event) => page.setSelectedServerId(event.target.value)}
                  disabled={page.isLoading || page.isUpdatingPlan}
                  className="min-w-48 rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text"
                >
                  {page.servers.map((server) => (
                    <option key={server.id} value={server.id}>
                      {server.name}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <p className="text-sm text-app-muted">{selectedServerName}</p>
            )}
            <button
              type="button"
              onClick={() => void page.refresh()}
              disabled={page.isLoading || page.isUpdatingPlan || !page.selectedServerId}
              className="rounded-lg border border-app-border px-3 py-2 text-sm text-app-muted hover:bg-app-elevated hover:text-app-text disabled:opacity-60"
            >
              Refresh
            </button>
          </div>
        </div>
      </header>

      <div className="flex-1 p-6">
        {page.isLoading && (
          <p className="text-sm text-app-muted">Loading instructor metrics…</p>
        )}

        {page.accessDenied && (
          <p className="rounded-lg border border-app-border bg-app-surface px-4 py-3 text-sm text-app-muted">
            Only Study Server owners and course instructors can open the Instructor Dashboard.
          </p>
        )}

        {page.error && !page.accessDenied && (
          <p
            role="alert"
            aria-live="assertive"
            className="mb-4 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200"
          >
            {page.error}
          </p>
        )}

        {page.actionMessage && (
          <p
            role="status"
            aria-live="polite"
            className="mb-4 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200"
          >
            {page.actionMessage}
          </p>
        )}

        {!page.isLoading && !page.accessDenied && page.dashboard && (
          <div className="flex flex-col gap-6">
            {page.dashboard.quotaExhausted && (
              <p
                role="status"
                aria-live="polite"
                className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm text-amber-100"
              >
                AI query quota exhausted for {SAAS_PLAN_LABELS[page.planTierDraft]}. Upgrade the
                SaaS plan or wait for the next reset cycle.
              </p>
            )}

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <MetricCard
                label="Unanswered support questions"
                value={page.dashboard.unansweredSupportQuestions}
                hint="Needs instructor or TA follow-up"
              />
              <MetricCard
                label="Repeated question groups"
                value={page.dashboard.repeatedQuestionGroups}
                hint="FAQ approval candidates"
              />
              <MetricCard
                label="TA queue load"
                value={page.dashboard.openTaQueueItems}
                hint="Open handoffs waiting for pickup"
              />
              <MetricCard
                label="Low-confidence handoffs"
                value={page.dashboard.lowConfidenceHandoffs}
                hint="AI escalations awaiting review"
              />
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
              <article className="rounded-xl border border-app-border bg-app-surface p-5">
                <h2 className="text-sm font-semibold text-app-text">Office Hours</h2>
                <dl className="mt-4 grid gap-3 text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-app-muted">Live sessions</dt>
                    <dd className="font-medium text-app-text">
                      {page.dashboard.liveOfficeHoursSessions}
                    </dd>
                  </div>
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-app-muted">Scheduled sessions</dt>
                    <dd className="font-medium text-app-text">
                      {page.dashboard.scheduledOfficeHoursSessions}
                    </dd>
                  </div>
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-app-muted">Waitlist entries</dt>
                    <dd className="font-medium text-app-text">
                      {page.dashboard.officeHoursWaitlistEntries}
                    </dd>
                  </div>
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-app-muted">Approved FAQs</dt>
                    <dd className="font-medium text-app-text">{page.dashboard.approvedFaqCount}</dd>
                  </div>
                </dl>
              </article>

              <article className="rounded-xl border border-app-border bg-app-surface p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-sm font-semibold text-app-text">SaaS plan &amp; billing</h2>
                    <p className="mt-1 text-xs text-app-muted">
                      AI usage limits for {selectedServerName}.
                    </p>
                  </div>
                  <span className="rounded-full border border-app-border px-2 py-1 text-xs text-app-muted">
                    {SAAS_PLAN_LABELS[page.planTierDraft]}
                  </span>
                </div>

                <div className="mt-5">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-app-muted">AI queries used</span>
                    <span className="font-medium text-app-text">
                      {page.dashboard.aiInvocationCount} / {page.dashboard.aiInvocationLimit}
                    </span>
                  </div>
                  <div className="mt-2 h-2 overflow-hidden rounded-full bg-app-elevated">
                    <div
                      className={cn(
                        'h-full rounded-full transition-all',
                        page.dashboard.quotaExhausted ? 'bg-amber-500' : 'bg-app-accent',
                      )}
                      style={{ width: aiUsagePercent }}
                    />
                  </div>
                  <p className="mt-2 text-xs text-app-muted">
                    {page.dashboard.remainingAiInvocations} remaining ({aiUsagePercent} of monthly
                    quota)
                  </p>
                </div>

                {page.isOwner ? (
                  <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-end">
                    <label className="flex flex-1 flex-col gap-1 text-xs text-app-muted">
                      Plan tier
                      <select
                        value={page.planTierDraft}
                        onChange={(event) =>
                          page.setPlanTierDraft(event.target.value as SaasPlanTier)
                        }
                        disabled={page.isUpdatingPlan}
                        className="rounded-lg border border-app-border bg-app-bg px-3 py-2 text-sm text-app-text"
                      >
                        {SAAS_PLAN_TIERS.map((tier) => (
                          <option key={tier} value={tier}>
                            {SAAS_PLAN_LABELS[tier]}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button
                      type="button"
                      onClick={() => void page.savePlan()}
                      disabled={page.isUpdatingPlan}
                      className="rounded-lg bg-app-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                    >
                      {page.isUpdatingPlan ? 'Updating…' : 'Update plan'}
                    </button>
                  </div>
                ) : (
                  <p className="mt-5 text-xs text-app-muted">
                    Only the Study Server owner can change the SaaS plan tier.
                  </p>
                )}
              </article>
            </div>
          </div>
        )}

        {!page.isLoading && page.servers.length === 0 && (
          <p className="text-sm text-app-muted">
            No study servers yet. Create one through onboarding (#56) or the API demo harness.
          </p>
        )}
      </div>
    </section>
  )
}
