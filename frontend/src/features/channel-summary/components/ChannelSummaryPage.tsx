import { Link, useParams } from 'react-router-dom'
import { useState } from 'react'

import { cn } from '../../../lib/cn'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { courseChannelPath, findCourseChannelContext } from '../../shell/shell-routes'
import {
  formatDeltaPercent,
  formatSummaryMetricValue,
} from '../channel-summary-api'
import type { ChannelSummary, ChannelSummaryMetric, ChannelSummaryTimelineEvent } from '../channel-summary-types'
import { useChannelSummary } from '../hooks/use-channel-summary'

type MetricCardProps = {
  label: string
  metricKey: keyof ChannelSummary['metrics']
  metric: ChannelSummaryMetric
  icon: string
}

function MetricCard({ label, metricKey, metric, icon }: MetricCardProps) {
  const trendPositive = metric.deltaPercent >= 0

  return (
    <article className="rounded-xl border border-app-border bg-app-surface p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">{label}</p>
          <p className="mt-2 text-2xl font-semibold text-app-text">
            {formatSummaryMetricValue(metricKey, metric.value)}
          </p>
          <p
            className={cn(
              'mt-1 text-xs font-medium',
              trendPositive ? 'text-emerald-300' : 'text-rose-300',
            )}
          >
            {formatDeltaPercent(metric.deltaPercent)}
          </p>
        </div>
        <span
          aria-hidden
          className="flex h-9 w-9 items-center justify-center rounded-lg bg-app-elevated text-sm text-app-accent"
        >
          {icon}
        </span>
      </div>
    </article>
  )
}

function DigestRow({
  title,
  body,
  badge,
}: {
  title: string
  body: string
  badge?: string
}) {
  return (
    <div className="flex items-start justify-between gap-3 rounded-lg border border-app-border bg-app-bg px-4 py-3">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm font-medium text-app-text">{title}</p>
          {badge ? (
            <span className="rounded-full bg-amber-500/20 px-2 py-0.5 text-xs font-semibold text-amber-200">
              {badge}
            </span>
          ) : null}
        </div>
        <p className="mt-1 text-sm text-app-muted">{body}</p>
      </div>
      <span aria-hidden className="text-app-muted">
        ›
      </span>
    </div>
  )
}

function timelineLabel(event: ChannelSummaryTimelineEvent): string {
  switch (event.type) {
    case 'NEW_QUESTION':
      return 'New question asked'
    case 'REPLY_ADDED':
      return 'Reply added'
    case 'QUESTION_RESOLVED':
      return 'Question resolved'
    case 'KEY_DECISION':
      return 'Key decision recorded'
    default:
      return 'Activity'
  }
}

function formatTimelineDate(iso: string): string {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(iso))
}

export function ChannelSummaryPage() {
  const { serverId, channelId } = useParams()
  const [windowDays, setWindowDays] = useState(7)
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const channelContext =
    channelId && navigationQuery.data
      ? findCourseChannelContext(navigationQuery.data, channelId)
      : null
  const canViewSummary = channelContext?.course.capabilities.canManageCourse ?? false
  const summaryState = useChannelSummary(channelId, windowDays)

  if (!serverId || !channelId) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Channel summary not found.
      </section>
    )
  }

  if (navigationQuery.isLoading) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Loading channel…
      </section>
    )
  }

  if (navigationQuery.isError) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Unable to load channel navigation for this Study Server.
      </section>
    )
  }

  if (!canViewSummary) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Only instructors can view channel summaries.
      </section>
    )
  }

  const summary = summaryState.summary

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-app-bg">
      <header className="border-b border-app-border px-6 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
              Channel Summary
            </p>
            <h1 className="mt-1 text-2xl font-semibold text-app-text">
              #{channelContext?.channel.name ?? 'questions'}
              <span className="ml-2 text-base font-normal text-app-muted">
                · Last {summary?.windowDays ?? windowDays} days
              </span>
            </h1>
            <p className="mt-1 max-w-2xl text-sm text-app-muted">
              A weekly digest of key activity, discussions, and outcomes in this channel.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label className="flex flex-col gap-1 text-xs text-app-muted">
              Time window
              <select
                value={windowDays}
                onChange={(event) => setWindowDays(Number(event.target.value))}
                disabled={summaryState.isGenerating}
                className="min-w-32 rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text"
              >
                <option value={7}>Last 7 days</option>
                <option value={14}>Last 14 days</option>
                <option value={30}>Last 30 days</option>
              </select>
            </label>
            <Link
              to={courseChannelPath(serverId, channelId)}
              className="rounded-lg border border-app-border px-3 py-2 text-sm text-app-muted hover:bg-app-surface hover:text-app-text"
            >
              Back to channel
            </Link>
            <button
              type="button"
              onClick={() => summaryState.generate()}
              disabled={summaryState.isGenerating}
              className="rounded-lg bg-app-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
            >
              {summaryState.isGenerating
                ? 'Generating…'
                : summaryState.hasGenerated
                  ? 'Refresh summary'
                  : 'Generate summary'}
            </button>
            <button
              type="button"
              onClick={() => window.print()}
              disabled={!summary}
              className="rounded-lg border border-app-border px-3 py-2 text-sm text-app-text disabled:opacity-50"
            >
              Export PDF
            </button>
          </div>
        </div>
      </header>

      {summaryState.error ? (
        <p className="border-b border-rose-500/30 bg-rose-500/10 px-6 py-3 text-sm text-rose-200">
          {summaryState.error}
        </p>
      ) : null}

      {!summary && !summaryState.isGenerating ? (
        <div className="flex flex-1 items-center justify-center p-8 text-center">
          <div className="max-w-md space-y-3">
            <p className="text-sm text-app-muted">
              Generate a digest to see metrics, top topics, follow-ups, and a timeline for this
              channel.
            </p>
            <button
              type="button"
              onClick={() => summaryState.generate()}
              className="rounded-lg bg-app-accent px-4 py-2 text-sm font-medium text-white"
            >
              Generate summary
            </button>
          </div>
        </div>
      ) : null}

      {summary ? (
        <div className="space-y-6 p-6">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              label="Questions asked"
              metricKey="questionsAsked"
              metric={summary.metrics.questionsAsked}
              icon="?"
            />
            <MetricCard
              label="Replies"
              metricKey="replies"
              metric={summary.metrics.replies}
              icon="↩"
            />
            <MetricCard
              label="Views"
              metricKey="views"
              metric={summary.metrics.views}
              icon="◎"
            />
            <MetricCard
              label="Resolved"
              metricKey="resolvedPercent"
              metric={summary.metrics.resolvedPercent}
              icon="✓"
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
            <section className="rounded-xl border border-app-border bg-app-surface p-5">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-sm font-semibold text-app-text">✨ AI generated digest</h2>
                <p className="text-xs text-app-muted">
                  Generated {formatTimelineDate(summary.generatedAt)}
                </p>
              </div>

              <div className="mt-4 space-y-3">
                <DigestRow
                  title={summary.digest.topTopics.title}
                  body={summary.digest.topTopics.summary}
                />
                <DigestRow
                  title="Unanswered follow-ups"
                  body={summary.digest.unansweredFollowUps.summary}
                  badge={String(summary.digest.unansweredFollowUps.count)}
                />
                <DigestRow
                  title="Key decisions"
                  body={
                    summary.digest.keyDecisions.items.length > 0
                      ? summary.digest.keyDecisions.items.join(' ')
                      : summary.digest.keyDecisions.summary
                  }
                />
                <DigestRow
                  title="Links to resources"
                  body={
                    summary.digest.resourceLinks.items.length > 0
                      ? summary.digest.resourceLinks.items.join(' · ')
                      : summary.digest.resourceLinks.summary
                  }
                />
              </div>
            </section>

            <section className="rounded-xl border border-app-border bg-app-surface p-5">
              <h2 className="text-sm font-semibold text-app-text">Timeline of activity</h2>
              <ul className="mt-4 space-y-4">
                {summary.timeline.map((event) => (
                  <li key={`${event.type}-${event.occurredAt}-${event.title}`} className="flex gap-3">
                    <span
                      aria-hidden
                      className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full bg-app-accent"
                    />
                    <div className="min-w-0">
                      <p className="text-xs font-semibold uppercase tracking-[0.08em] text-app-muted">
                        {timelineLabel(event)}
                      </p>
                      <p className="mt-1 text-sm text-app-text">{event.title}</p>
                      <p className="mt-1 text-xs text-app-muted">
                        {formatTimelineDate(event.occurredAt)}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        </div>
      ) : null}
    </section>
  )
}
