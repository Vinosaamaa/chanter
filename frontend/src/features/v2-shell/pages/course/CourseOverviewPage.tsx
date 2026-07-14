import { CalendarDays, Check, ClipboardList, FileText, MessageSquare, Play, Radio } from 'lucide-react'
import { Link } from 'react-router-dom'

import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'
import { v2CoursePath } from '../../v2-routes'

export function CourseOverviewPage() {
  const { serverId, courseId, course, selectedCohort } = useV2CourseWorkspace()
  const voiceChannel = course.channels.find(
    (channel) => channel.kind === 'VOICE'
      && (!channel.cohortId || channel.cohortId === selectedCohort?.id),
  )
  const cohortQuery = selectedCohort ? `?cohort=${encodeURIComponent(selectedCohort.id)}` : ''
  const voicePath = voiceChannel
    ? `${v2CoursePath(serverId, courseId, 'chat')}?${new URLSearchParams({
        ...(selectedCohort ? { cohort: selectedCohort.id } : {}),
        channel: voiceChannel.id,
      })}`
    : null

  return (
    <div className="course-overview-layout">
      <div className="overview-main-column">
        <article className="v2-panel progress-panel">
          <h2>Your progress</h2>
          <strong>65%</strong>
          <div className="overview-progress"><span /></div>
          <p>Week 3 of 12</p>
        </article>
        <article className="v2-panel this-week-panel">
          <h2>This week</h2>
          <p><span className="round-icon green"><Check /></span>Lecture 3 — Recursion</p>
          <p><span className="round-icon amber"><ClipboardList /></span>Problem Set 2 <b>Due Sunday 11:59 PM</b></p>
          <p><span className="round-icon purple"><CalendarDays /></span>Office hours today 2:00 PM</p>
        </article>
        <article className="v2-panel recent-panel">
          <h2>Recent activity</h2>
          <p><V2Avatar name="Maria" tone="purple" size="sm" /> <span><strong>Maria</strong> answered your question in Questions</span><time>2h ago</time></p>
          <p><span className="square-icon blue"><FileText /></span><span>New resource&nbsp; Lecture 2 slides</span><time>yesterday</time></p>
        </article>
      </div>
      <aside className="v2-panel course-up-next">
        <h2>Up next</h2>
        <div className="course-timeline">
          <article><i className="blue" /><div><small>Today <b>2:00 PM</b></small><p><span className="round-icon purple"><CalendarDays /></span>Office hours <Link aria-label="Open Office hours" to={`${v2CoursePath(serverId, courseId, 'office-hours')}${cohortQuery}`}>Open</Link></p></div></article>
          <article><i className="amber" /><div><small>Sunday</small><p><span className="round-icon amber"><ClipboardList /></span>Problem Set 2 <b className="due-badge">Due Sunday 11:59 PM</b></p></div></article>
          <article><i className="purple" /><div><small>Wed</small><p><span className="round-icon purple"><Play /></span>Lecture 4 —<br />Sorting algorithms</p></div></article>
          <article><i className="green" /><div><small>{voicePath ? 'Available now' : 'Unavailable'}</small><p><span className="round-icon blue"><Radio /></span>Study room {voicePath ? <Link aria-label="Join Study room" to={voicePath}>Join</Link> : <button type="button" disabled title="No voice channel is available for this Cohort">Join</button>}</p></div></article>
        </div>
      </aside>
      <span className="overview-accessibility-copy"><MessageSquare /> Course activity</span>
    </div>
  )
}
