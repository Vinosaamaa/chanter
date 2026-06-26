import { Link, useLocation, useParams } from 'react-router-dom'

import { cn } from '../../../lib/cn'
import { useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import {
  courseChannelPath,
  isSupportOperation,
  SUPPORT_OPERATIONS,
  studyChannelPath,
  supportOperationLabel,
  supportOperationPath,
} from '../shell-routes'
import type { ShellChannel } from '../types'

export function ChannelSidebarColumn() {
  const { serverId, channelId, courseId, operation } = useParams()
  const location = useLocation()
  const channelScope = location.pathname.includes('/course-channels/')
    ? 'course'
    : location.pathname.includes('/study-channels/')
      ? 'study'
      : location.pathname.includes('/courses/') && location.pathname.includes('/support/')
        ? 'support'
        : undefined
  const navigationQuery = useStudyServerNavigationQuery(serverId)

  if (!serverId) {
    return null
  }

  return (
    <aside className="flex w-60 shrink-0 flex-col border-r border-app-border bg-app-surface">
      <div className="border-b border-app-border px-4 py-3">
        <p className="truncate text-sm font-semibold text-app-text">
          {navigationQuery.data?.studyServerName ?? 'Study Server'}
        </p>
        <p className="text-xs text-app-muted">Enrollment-scoped navigation</p>
      </div>

      <div className="flex-1 overflow-y-auto p-2">
        {navigationQuery.isLoading && (
          <p className="px-2 py-1 text-xs text-app-muted">Loading channels…</p>
        )}
        {navigationQuery.isError && (
          <p className="px-2 py-1 text-xs text-red-300">Could not load navigation.</p>
        )}

        {navigationQuery.data && navigationQuery.data.studyServerChannels.length > 0 && (
          <section className="mb-4">
            <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-app-muted">
              Study Server
            </p>
            <ul className="flex flex-col gap-0.5">
              {navigationQuery.data.studyServerChannels.map((channel) => (
                <ChannelLink
                  key={channel.id}
                  channel={channel}
                  to={studyChannelPath(serverId, channel.id)}
                  isActive={channelScope === 'study' && channel.id === channelId}
                />
              ))}
            </ul>
          </section>
        )}

        {navigationQuery.data && navigationQuery.data.courses.length > 0 && (
          <section>
            <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-app-muted">
              {navigationQuery.data.canViewFullCatalog ? 'Courses' : 'My courses'}
            </p>
            <ul className="flex flex-col gap-2">
              {navigationQuery.data.courses.map((course) => (
                <li key={course.id}>
                  <p className="truncate px-2 text-xs font-medium text-app-text">
                    {course.title}
                    {course.cohorts[0] ? (
                      <span className="block truncate text-[11px] font-normal text-app-muted">
                        {course.cohorts[0].name}
                      </span>
                    ) : null}
                  </p>
                  <ul className="mt-1 flex flex-col gap-0.5">
                    {course.channels.map((channel) => (
                      <ChannelLink
                        key={channel.id}
                        channel={channel}
                        to={courseChannelPath(serverId, channel.id)}
                        isActive={channelScope === 'course' && channel.id === channelId}
                      />
                    ))}
                  </ul>
                  <ul className="mt-2 flex flex-col gap-0.5 border-t border-app-border/60 pt-2">
                    <li className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-app-muted">
                      Support
                    </li>
                    {SUPPORT_OPERATIONS.map((item) => (
                      <li key={item}>
                        <Link
                          to={supportOperationPath(serverId, course.id, item)}
                          className={cn(
                            'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors',
                            channelScope === 'support' &&
                              courseId === course.id &&
                              isSupportOperation(operation) &&
                              operation === item
                              ? 'bg-app-elevated text-app-text'
                              : 'text-app-muted hover:bg-app-elevated/70 hover:text-app-text',
                          )}
                        >
                          <span aria-hidden className="w-4 shrink-0 text-center text-xs text-app-muted">
                            •
                          </span>
                          <span className="truncate">{supportOperationLabel(item)}</span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          </section>
        )}

        {navigationQuery.data
          && navigationQuery.data.studyServerChannels.length === 0
          && navigationQuery.data.courses.length === 0 && (
          <p className="px-2 py-1 text-xs text-app-muted">
            No channels yet. Create a course or join a cohort to get started.
          </p>
        )}
      </div>
    </aside>
  )
}

function ChannelLink({
  channel,
  to,
  isActive,
}: {
  channel: ShellChannel
  to: string
  isActive: boolean
}) {
  return (
    <li>
      <Link
        to={to}
        className={cn(
          'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors',
          isActive
            ? 'bg-app-elevated text-app-text'
            : 'text-app-muted hover:bg-app-elevated/70 hover:text-app-text',
        )}
      >
        <span aria-hidden className="w-4 shrink-0 text-center text-xs text-app-muted">
          {channel.kind === 'VOICE' ? '🔊' : '#'}
        </span>
        <span className="truncate">{channel.name}</span>
      </Link>
    </li>
  )
}
