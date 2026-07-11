import { Link, useLocation, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'

import { cn } from '../../../lib/cn'
import { useShellLayoutStore } from '../../../stores/shell-layout-store'
import { useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import {
  channelIcon,
  courseChannelGroup,
  courseChannelGroupLabel,
  courseChannelPath,
  isSupportOperation,
  SUPPORT_OPERATIONS,
  studyChannelGroup,
  studyChannelPath,
  supportOperationLabel,
  supportOperationPath,
} from '../shell-routes'
import type { ShellChannel } from '../types'

function useSectionCollapsed(sectionKey: string): boolean {
  return useShellLayoutStore((state) => state.collapsedSidebarSections.includes(sectionKey))
}

export function ChannelSidebarColumn() {
  const { serverId, channelId, courseId, operation } = useParams()
  const location = useLocation()
  const channelScope = location.pathname.includes('/course-channels/')
    ? 'course'
    : location.pathname.includes('/study-channels/')
      ? 'study'
      : location.pathname.includes('/courses/') && location.pathname.includes('/support/')
        ? 'support'
        : location.pathname.endsWith('/home') || location.pathname.includes('/enrollment')
          ? 'home'
          : undefined
  const navigationQuery = useStudyServerNavigationQuery(serverId)

  if (!serverId) {
    return null
  }

  const scopeLabel =
    channelScope === 'support'
      ? 'Support operations'
      : channelScope === 'study'
        ? 'Study Server channels'
        : channelScope === 'course'
          ? 'Course channels'
          : channelScope === 'home'
            ? null
            : 'Server navigation'

  return (
    <aside className="flex h-full min-h-0 flex-col border-r border-app-border bg-app-surface">
      <div className="border-b border-app-border px-4 py-3">
        <Link
          to={`/app/servers/${serverId}/home`}
          className="truncate text-sm font-semibold text-app-text hover:text-app-accent"
        >
          {navigationQuery.data?.studyServerName ?? 'Study Server'}
        </Link>
        {scopeLabel ? <p className="text-xs text-app-muted">{scopeLabel}</p> : null}
      </div>

      <div className="flex-1 overflow-y-auto p-2">
        {navigationQuery.isLoading && (
          <p className="px-2 py-1 text-xs text-app-muted">Loading channels…</p>
        )}
        {navigationQuery.isError && (
          <p className="px-2 py-1 text-xs text-red-300">Could not load navigation.</p>
        )}

        {navigationQuery.data && navigationQuery.data.studyServerChannels.length > 0 && (
          <StudyServerChannelsSection
            serverId={serverId}
            channels={navigationQuery.data.studyServerChannels}
            channelScope={channelScope}
            channelId={channelId}
          />
        )}

        {navigationQuery.data && navigationQuery.data.courses.length > 0 && (
          <section>
            <CollapsibleSidebarHeader
              label={navigationQuery.data.canViewFullCatalog ? 'Courses' : 'My courses'}
              sectionKey={`${serverId}:courses`}
            />
            <CoursesSection
              serverId={serverId}
              channelScope={channelScope}
              channelId={channelId}
              courseId={courseId}
              operation={operation}
              courses={navigationQuery.data.courses}
              coursesSectionKey={`${serverId}:courses`}
            />
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

function StudyServerChannelsSection({
  serverId,
  channels,
  channelScope,
  channelId,
}: {
  serverId: string
  channels: ShellChannel[]
  channelScope: string | undefined
  channelId: string | undefined
}) {
  const sectionKey = `${serverId}:study-server`
  const collapsed = useSectionCollapsed(sectionKey)

  return (
    <section className="mb-3">
      <CollapsibleSidebarHeader label="Study Server" sectionKey={sectionKey} />
      {!collapsed ? (
        <ChannelGroupList
          sectionKeyPrefix={sectionKey}
          channels={channels}
          getGroup={studyChannelGroup}
          renderChannel={(channel) => (
            <ChannelLink
              channel={channel}
              to={studyChannelPath(serverId, channel.id)}
              isActive={channelScope === 'study' && channel.id === channelId}
              indent
            />
          )}
        />
      ) : null}
    </section>
  )
}

function CollapsibleSidebarHeader({
  label,
  sectionKey,
  subtitle,
  variant = 'section',
}: {
  label: string
  sectionKey: string
  subtitle?: string
  variant?: 'section' | 'course' | 'group'
}) {
  const collapsed = useSectionCollapsed(sectionKey)
  const toggleSidebarSection = useShellLayoutStore((state) => state.toggleSidebarSection)

  return (
    <button
      type="button"
      onClick={() => toggleSidebarSection(sectionKey)}
      aria-expanded={!collapsed}
      className={cn(
        'flex w-full items-center gap-1.5 rounded-md text-left transition-colors hover:bg-app-bg/60',
        variant === 'section' && 'px-2 py-1.5',
        variant === 'course' && 'mt-2 px-2 py-1.5',
        variant === 'group' && 'px-2 py-1',
      )}
    >
      <span
        aria-hidden
        className={cn(
          'shrink-0 text-[10px] text-app-muted transition-transform',
          !collapsed && 'rotate-90',
        )}
      >
        ▸
      </span>
      <span className="min-w-0 flex-1">
        <span
          className={cn(
            'block truncate',
            variant === 'section' &&
              'text-[11px] font-semibold uppercase tracking-[0.14em] text-app-muted',
            variant === 'course' && 'text-sm font-semibold text-app-text',
            variant === 'group' &&
              'text-[10px] font-semibold uppercase tracking-[0.12em] text-app-muted',
          )}
        >
          {label}
        </span>
        {subtitle ? (
          <span className="mt-0.5 block truncate text-[11px] text-app-muted">{subtitle}</span>
        ) : null}
      </span>
    </button>
  )
}

function CoursesSection({
  serverId,
  channelScope,
  channelId,
  courseId,
  operation,
  courses,
  coursesSectionKey,
}: {
  serverId: string
  channelScope: string | undefined
  channelId: string | undefined
  courseId: string | undefined
  operation: string | undefined
  courses: Array<{
    id: string
    title: string
    cohorts: Array<{ name: string }>
    channels: ShellChannel[]
  }>
  coursesSectionKey: string
}) {
  const coursesCollapsed = useSectionCollapsed(coursesSectionKey)

  if (coursesCollapsed) {
    return null
  }

  return (
    <ul className="flex flex-col">
      {courses.map((course) => (
        <li key={course.id}>
          <CollapsibleSidebarHeader
            label={course.title}
            subtitle={course.cohorts[0]?.name}
            sectionKey={`course:${course.id}`}
            variant="course"
          />
          <CourseChannels
            serverId={serverId}
            course={course}
            channelScope={channelScope}
            channelId={channelId}
            courseId={courseId}
            operation={operation}
          />
        </li>
      ))}
    </ul>
  )
}

function CourseChannels({
  serverId,
  course,
  channelScope,
  channelId,
  courseId,
  operation,
}: {
  serverId: string
  course: {
    id: string
    title: string
    channels: ShellChannel[]
  }
  channelScope: string | undefined
  channelId: string | undefined
  courseId: string | undefined
  operation: string | undefined
}) {
  const courseCollapsed = useSectionCollapsed(`course:${course.id}`)
  const supportCollapsed = useSectionCollapsed(`course:${course.id}:support`)

  if (courseCollapsed) {
    return null
  }

  return (
    <div className="pb-1 pl-1">
      <ChannelGroupList
        sectionKeyPrefix={`course:${course.id}`}
        channels={course.channels}
        getGroup={courseChannelGroup}
        renderChannel={(channel) => (
          <ChannelLink
            channel={channel}
            to={courseChannelPath(serverId, channel.id)}
            isActive={channelScope === 'course' && channel.id === channelId}
            indent
          />
        )}
      />

      <CollapsibleSidebarHeader
        label="Support"
        sectionKey={`course:${course.id}:support`}
        variant="group"
      />
      {!supportCollapsed ? (
        <ul className="flex flex-col gap-0.5">
          {SUPPORT_OPERATIONS.map((item) => {
            const isActiveSupportLink =
              channelScope === 'support' &&
              courseId === course.id &&
              isSupportOperation(operation) &&
              operation === item

            return (
              <li key={item}>
                <Link
                  to={supportOperationPath(serverId, course.id, item)}
                  aria-current={isActiveSupportLink ? 'page' : undefined}
                  className={cn(
                    'flex items-center gap-2 rounded-md py-1.5 pl-6 pr-2 text-sm transition-colors',
                    isActiveSupportLink
                      ? 'bg-app-bg text-app-text'
                      : 'text-app-muted hover:bg-app-bg/70 hover:text-app-text',
                  )}
                >
                  <span aria-hidden className="w-4 shrink-0 text-center text-xs text-app-muted">
                    •
                  </span>
                  <span className="truncate">{supportOperationLabel(item)}</span>
                </Link>
              </li>
            )
          })}
        </ul>
      ) : null}
    </div>
  )
}

function ChannelGroupList<T extends ShellChannel>({
  channels,
  getGroup,
  renderChannel,
  sectionKeyPrefix,
}: {
  channels: T[]
  getGroup: (channel: T) => ReturnType<typeof courseChannelGroup>
  renderChannel: (channel: T) => ReactNode
  sectionKeyPrefix: string
}) {
  return (
    <>
      {(['information', 'text', 'voice'] as const).map((group) => (
        <ChannelGroupRow
          key={`${sectionKeyPrefix}:${group}`}
          group={group}
          channels={channels}
          getGroup={getGroup}
          renderChannel={renderChannel}
          sectionKey={`${sectionKeyPrefix}:${group}`}
        />
      ))}
    </>
  )
}

function ChannelGroupRow<T extends ShellChannel>({
  group,
  channels,
  getGroup,
  renderChannel,
  sectionKey,
}: {
  group: ReturnType<typeof courseChannelGroup>
  channels: T[]
  getGroup: (channel: T) => ReturnType<typeof courseChannelGroup>
  renderChannel: (channel: T) => ReactNode
  sectionKey: string
}) {
  const collapsed = useSectionCollapsed(sectionKey)
  const groupedChannels = channels.filter((channel) => getGroup(channel) === group)

  if (groupedChannels.length === 0) {
    return null
  }

  return (
    <div className="mb-1">
      <CollapsibleSidebarHeader
        label={courseChannelGroupLabel(group)}
        sectionKey={sectionKey}
        variant="group"
      />
      {!collapsed ? (
        <ul className="flex flex-col gap-0.5">
          {groupedChannels.map((channel) => (
            <li key={channel.id}>{renderChannel(channel)}</li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function ChannelLink({
  channel,
  to,
  isActive,
  indent = false,
}: {
  channel: ShellChannel
  to: string
  isActive: boolean
  indent?: boolean
}) {
  return (
    <Link
      to={to}
      aria-current={isActive ? 'page' : undefined}
      className={cn(
        'flex items-center gap-2 rounded-md py-1.5 pr-2 text-sm transition-colors',
        indent ? 'pl-6' : 'px-2',
        isActive
          ? 'bg-app-bg text-app-text'
          : 'text-app-muted hover:bg-app-bg/70 hover:text-app-text',
      )}
    >
      <span aria-hidden className="w-4 shrink-0 text-center text-xs text-app-muted">
        {channelIcon(channel)}
      </span>
      <span className="truncate">{channel.name}</span>
    </Link>
  )
}
