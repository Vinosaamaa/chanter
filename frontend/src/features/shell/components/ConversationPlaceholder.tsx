import { useLocation, useParams } from 'react-router-dom'

import { useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import { findChannelLabel } from '../shell-routes'

export function ConversationPlaceholder() {
  const { serverId, channelId } = useParams()
  const location = useLocation()
  const channelScope = location.pathname.includes('/course-channels/')
    ? 'course'
    : location.pathname.includes('/study-channels/')
      ? 'study'
      : null
  const navigationQuery = useStudyServerNavigationQuery(serverId)

  const channelLabel = channelScope && channelId
    ? findChannelLabel(navigationQuery.data, channelScope, channelId)
    : null

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <div className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Conversation
        </p>
        <h2 className="mt-1 text-base font-semibold text-app-text">
          {channelLabel ?? 'Select a channel'}
        </h2>
        <p className="text-xs text-app-muted">Live chat lands in #51.</p>
      </div>
      <div className="flex flex-1 items-center justify-center p-6">
        <p className="max-w-md text-center text-sm text-app-muted">
          {channelLabel
            ? `Messages for ${channelLabel} will appear here once realtime chat ships.`
            : 'Pick a study server channel or a course channel from the sidebar.'}
        </p>
      </div>
    </section>
  )
}
