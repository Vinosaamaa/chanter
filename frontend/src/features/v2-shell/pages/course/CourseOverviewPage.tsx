import { useQuery } from '@tanstack/react-query'
import { CalendarDays, Check, FileText, MessageSquare, Radio } from 'lucide-react'
import { Link } from 'react-router-dom'

import {
  courseOverviewSummaryQueryKey,
  fetchCourseOverviewSummary,
} from '../../../course-overview/course-overview-summary-api'
import type { CourseOverviewItem } from '../../../course-overview/course-overview-summary-types'
import { formatUserFacingApiError } from '../../../../lib/format-api-error'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'
import { v2CoursePath } from '../../v2-routes'

function thisWeekIcon(kind: string) {
  switch (kind) {
    case 'OFFICE_HOURS':
      return <CalendarDays />
    case 'STUDY_ROOM':
      return <Radio />
    default:
      return <Check />
  }
}

function thisWeekTone(kind: string) {
  switch (kind) {
    case 'OFFICE_HOURS':
      return 'purple'
    case 'STUDY_ROOM':
      return 'blue'
    default:
      return 'green'
  }
}

function upNextTone(kind: string) {
  switch (kind) {
    case 'STUDY_ROOM':
      return 'green'
    case 'OFFICE_HOURS':
      return 'blue'
    default:
      return 'purple'
  }
}

function RecentIcon({ kind }: { kind: string }) {
  if (kind === 'RESOURCE') {
    return <span className="square-icon blue"><FileText /></span>
  }
  return <V2Avatar name="Member" tone="purple" size="sm" />
}

export function CourseOverviewPage() {
  const { serverId, courseId, course, selectedCohort } = useV2CourseWorkspace()
  const voiceChannel = course.channels.find(
    (channel) => channel.kind === 'VOICE'
      && (!channel.cohortId || channel.cohortId === selectedCohort?.id),
  )
  const fallbackVoicePath = voiceChannel
    ? `${v2CoursePath(serverId, courseId, 'chat')}?${new URLSearchParams({
        ...(selectedCohort ? { cohort: selectedCohort.id } : {}),
        channel: voiceChannel.id,
      })}`
    : null

  const summaryQuery = useQuery({
    queryKey: courseOverviewSummaryQueryKey(courseId, selectedCohort?.id),
    queryFn: () => fetchCourseOverviewSummary(courseId, selectedCohort?.id),
    enabled: Boolean(courseId),
  })

  const summary = summaryQuery.data
  const thisWeek = summary?.thisWeek ?? []
  const recentActivity = summary?.recentActivity ?? []
  const upNext = summary?.upNext ?? []
  const progressUnavailable = summary?.progress == null
  const studyRoomItem = upNext.find((item) => item.kind === 'STUDY_ROOM')
  const studyRoomHref = studyRoomItem?.href ?? fallbackVoicePath

  return (
    <div className="course-overview-layout">
      <div className="overview-main-column">
        <article className="v2-panel progress-panel">
          <h2>Your progress</h2>
          {summaryQuery.isLoading ? (
            <p>Loading…</p>
          ) : progressUnavailable ? (
            <>
              <p>Progress is unavailable until a curriculum is published for this course.</p>
              {summary?.progressUnavailableReason ? (
                <p><small>{summary.progressUnavailableReason}</small></p>
              ) : null}
            </>
          ) : (
            <>
              <strong>{summary?.progress}%</strong>
              <div className="overview-progress"><span style={{ width: `${summary?.progress}%` }} /></div>
            </>
          )}
        </article>
        <article className="v2-panel this-week-panel">
          <h2>This week</h2>
          {summaryQuery.isLoading ? (
            <p>Loading…</p>
          ) : thisWeek.length === 0 ? (
            <p>Nothing scheduled this week.</p>
          ) : (
            thisWeek.map((item) => (
              <p key={item.id}>
                <span className={`round-icon ${thisWeekTone(item.kind)}`}>
                  {thisWeekIcon(item.kind)}
                </span>
                {item.href ? (
                  <Link to={item.href}>{item.title}</Link>
                ) : (
                  item.title
                )}
                {item.detail ? <span> · {item.detail}</span> : null}
              </p>
            ))
          )}
        </article>
        <article className="v2-panel recent-panel">
          <h2>Recent activity</h2>
          {summaryQuery.isLoading ? (
            <p>Loading…</p>
          ) : recentActivity.length === 0 ? (
            <p>No recent activity yet.</p>
          ) : (
            recentActivity.map((item: CourseOverviewItem) => (
              <p key={item.id}>
                <RecentIcon kind={item.kind} />
                <span>
                  {item.href ? <Link to={item.href}>{item.title}</Link> : item.title}
                  {item.detail ? <> · {item.detail}</> : null}
                </span>
              </p>
            ))
          )}
        </article>
      </div>
      <aside className="v2-panel course-up-next">
        <h2>Up next</h2>
        {summaryQuery.isError ? (
          <p>{formatUserFacingApiError(summaryQuery.error, 'Unable to load overview.')}</p>
        ) : summaryQuery.isLoading ? (
          <p>Loading…</p>
        ) : upNext.length === 0 && !studyRoomHref ? (
          <p>Nothing coming up yet.</p>
        ) : (
          <div className="course-timeline">
            {upNext
              .filter((item) => item.kind !== 'STUDY_ROOM')
              .map((item) => (
                <article key={item.id}>
                  <i className={upNextTone(item.kind)} />
                  <div>
                    <small>{item.detail}</small>
                    <p>
                      <span className={`round-icon ${thisWeekTone(item.kind)}`}>
                        {thisWeekIcon(item.kind)}
                      </span>
                      {item.title}{' '}
                      {item.href && item.actionLabel ? (
                        <Link aria-label={`${item.actionLabel} ${item.title}`} to={item.href}>
                          {item.actionLabel}
                        </Link>
                      ) : null}
                    </p>
                  </div>
                </article>
              ))}
            <article>
              <i className="green" />
              <div>
                <small>{studyRoomHref ? (studyRoomItem?.detail ?? 'Available now') : 'Unavailable'}</small>
                <p>
                  <span className="round-icon blue"><Radio /></span>
                  Study room{' '}
                  {studyRoomHref ? (
                    <Link aria-label="Join Study room" to={studyRoomHref}>Join</Link>
                  ) : (
                    <button type="button" disabled title="No voice channel is available for this Cohort">
                      Join
                    </button>
                  )}
                </p>
              </div>
            </article>
          </div>
        )}
      </aside>
      <span className="overview-accessibility-copy"><MessageSquare /> Course activity</span>
    </div>
  )
}
