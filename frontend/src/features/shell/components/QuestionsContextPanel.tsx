import { useQuery } from '@tanstack/react-query'
import { useLocation, useParams } from 'react-router-dom'

import { useAuthStore } from '../../../stores/auth-store'
import { fetchStudyAssistantPresence } from '../../questions/questions-api'
import { useQuestionsPanel } from '../context/use-questions-panel'
import { useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import { findCourseChannelContext, isQuestionsChannel } from '../shell-routes'

import { ContextPlaceholder } from './ContextPlaceholder'

export function ShellContextPanel() {
  const { serverId, channelId } = useParams()
  const location = useLocation()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const channelContext =
    location.pathname.includes('/course-channels/') && channelId
      ? findCourseChannelContext(navigationQuery.data, channelId)
      : null

  if (!isQuestionsChannel(channelContext)) {
    return <ContextPlaceholder />
  }

  return <QuestionsContextPanel studyServerId={serverId} />
}

function QuestionsContextPanel({ studyServerId }: { studyServerId: string | undefined }) {
  const userId = useAuthStore((state) => state.user?.id)
  const { selectedAnswer, studyServerId: panelServerId } = useQuestionsPanel()
  const resolvedServerId = studyServerId ?? panelServerId

  const assistantQuery = useQuery({
    queryKey: ['study-assistant-presence', resolvedServerId, userId],
    queryFn: () => fetchStudyAssistantPresence(resolvedServerId!, userId!),
    enabled: Boolean(resolvedServerId && userId),
  })

  return (
    <aside className="hidden w-80 shrink-0 flex-col border-l border-app-border bg-app-surface lg:flex">
      <div className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          AI context
        </p>
        <h2 className="mt-1 text-sm font-semibold text-app-text">Study Assistant</h2>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto p-4">
        <section className="rounded-lg border border-app-border bg-app-bg p-3">
          <h3 className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">
            Install status
          </h3>
          {assistantQuery.isLoading ? (
            <p className="mt-2 text-xs text-app-muted">Loading assistant grants…</p>
          ) : assistantQuery.isError ? (
            <p className="mt-2 text-xs text-rose-200">Could not load AI Study Assistant status.</p>
          ) : assistantQuery.data?.installed ? (
            <div className="mt-2 space-y-2 text-xs text-app-text">
              <p>Installed in this Study Server.</p>
              <p className="text-app-muted">
                {assistantQuery.data.grants.length} grant
                {assistantQuery.data.grants.length === 1 ? '' : 's'} active for channels and resources.
              </p>
              <ul className="space-y-1 text-app-muted">
                {assistantQuery.data.grants.slice(0, 4).map((grant) => (
                  <li key={`${grant.grantType}-${grant.grantTargetId}`}>
                    {grant.grantType.replaceAll('_', ' ').toLowerCase()}
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="mt-2 text-xs text-app-muted">
              AI Study Assistant is not installed yet. An instructor can install it from the dev demo
              harness until onboarding lands in #56.
            </p>
          )}
        </section>

        <section className="rounded-lg border border-app-border bg-app-bg p-3">
          <h3 className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">
            Grounding sources
          </h3>
          {selectedAnswer && selectedAnswer.sources.length > 0 ? (
            <ul className="mt-2 space-y-2">
              {selectedAnswer.sources.map((source) => (
                <li
                  key={source.resourceId}
                  className="rounded-md border border-app-border bg-app-surface px-2 py-2"
                >
                  <p className="text-sm font-medium text-app-text">{source.resourceTitle}</p>
                  <p className="mt-1 text-xs text-app-muted">{source.excerpt}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-xs text-app-muted">
              Ask AI on a support question to see grounded citation cards here.
            </p>
          )}
        </section>

        {selectedAnswer?.handoffRecommended ? (
          <section className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3">
            <h3 className="text-xs font-semibold uppercase tracking-[0.12em] text-amber-200">
              Low confidence
            </h3>
            <p className="mt-2 text-xs text-amber-100">
              The assistant recommends human review. Use <strong>Add to TA Queue</strong> in the
              conversation when you want a teaching assistant to follow up.
            </p>
          </section>
        ) : null}
      </div>
    </aside>
  )
}
