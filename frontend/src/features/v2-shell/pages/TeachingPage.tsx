import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  CalendarDays,
  ChevronRight,
  CircleHelp,
  ClipboardCheck,
  Clock,
  FileText,
  Sparkles,
  UsersRound,
} from 'lucide-react'
import { Navigate, useNavigate } from 'react-router-dom'

import type { TeachingCourseSummary } from '../../instructor-dashboard/instructor-dashboard-types'
import { useInstructorDashboardPage } from '../../instructor-dashboard/hooks/use-instructor-dashboard-page'
import { listOfficeHoursSessions } from '../../support-operations/office-hours-api'
import type { OfficeHoursSession } from '../../support-operations/support-operations-types'
import { useAuthStore } from '../../../stores/auth-store'
import { useV2SidebarData } from '../hooks/use-v2-sidebar-data'

type TeachingOfficeHoursSession = {
  course: TeachingCourseSummary
  cohortId: string
  session: OfficeHoursSession
}

const COURSE_COLORS = ['#526fff', '#31a66c', '#d28b3f', '#45a4c6']

function courseContextPath(
  serverId: string,
  course: TeachingCourseSummary,
  tab: 'questions' | 'resources' | 'office-hours' | 'people',
  cohortId = course.cohorts[0]?.cohortId,
  sessionId?: string,
) {
  const params = new URLSearchParams()
  if (cohortId) params.set('cohort', cohortId)
  if (sessionId) params.set('session', sessionId)
  const search = params.size > 0 ? `?${params.toString()}` : ''
  return `/app/servers/${serverId}/courses/${course.courseId}/${tab}${search}`
}

function formatSessionTime(startsAt: string) {
  return new Intl.DateTimeFormat(undefined, {
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(startsAt))
}

export function TeachingPage() {
  const access = useV2SidebarData()
  if (access.isLoading) {
    return (
      <section className="v2-workspace-page course-workspace-state" role="status">
        <p>Loading teaching...</p>
      </section>
    )
  }
  if (!access.showTeachingNav) return <Navigate to="/app/home" replace />
  return <TeachingContent />
}

function TeachingContent() {
  const [selectedServerId, setSelectedServerId] = useState<string | null>(null)
  const selectServer = useCallback((id: string) => setSelectedServerId(id), [])
  const page = useInstructorDashboardPage(selectedServerId, selectServer)
  const user = useAuthStore((state) => state.user)
  const navigate = useNavigate()
  const dashboard = page.dashboard
  const courses = useMemo(() => dashboard?.courses ?? [], [dashboard?.courses])
  const serverId = page.selectedServerId
  const officeHoursKey = `${serverId ?? ''}:${courses.map((course) => course.courseId).join(':')}`
  const [officeHoursState, setOfficeHoursState] = useState<{
    key: string
    sessions: TeachingOfficeHoursSession[]
    error: string | null
  }>({ key: '', sessions: [], error: null })
  const officeHours = officeHoursState.key === officeHoursKey ? officeHoursState.sessions : []
  const officeHoursError = officeHoursState.key === officeHoursKey ? officeHoursState.error : null

  useEffect(() => {
    if (courses.length === 0) return

    let cancelled = false
    const cohortContexts = courses.flatMap((course) =>
      course.cohorts.map((cohort) => ({ course, cohortId: cohort.cohortId })),
    )

    void Promise.allSettled(
      cohortContexts.map(async ({ course, cohortId }) => {
        const response = await listOfficeHoursSessions(cohortId)
        return response.officeHoursSessions.map((session) => ({ course, cohortId, session }))
      }),
    ).then((results) => {
      if (cancelled) return
      const available = results.flatMap((result) => result.status === 'fulfilled' ? result.value : [])
      available.sort((left, right) => {
        if (left.session.status === 'LIVE' && right.session.status !== 'LIVE') return -1
        if (right.session.status === 'LIVE' && left.session.status !== 'LIVE') return 1
        return new Date(left.session.startsAt).getTime() - new Date(right.session.startsAt).getTime()
      })
      setOfficeHoursState({
        key: officeHoursKey,
        sessions: available.filter(
          ({ session }) => session.status === 'LIVE' || session.status === 'SCHEDULED',
        ),
        error: results.some((result) => result.status === 'rejected')
          ? 'Some Office Hours schedules could not be loaded.'
          : null,
      })
    })

    return () => {
      cancelled = true
    }
  }, [courses, officeHoursKey])

  const used = dashboard?.aiInvocationCount ?? 0
  const limit = dashboard?.aiInvocationLimit ?? 0
  const percent = limit > 0 ? Math.min(100, Math.round((used / limit) * 100)) : 0
  const firstCourse = courses[0]
  const openQuestionCourse = courses.find((course) => course.unansweredSupportQuestions > 0) ?? firstCourse
  const faqCourse = courses.find((course) => course.repeatedQuestionGroups > 0) ?? firstCourse
  const queueCourse = courses.find((course) => course.openTaQueueItems > 0) ?? firstCourse
  const queueCohort = queueCourse?.cohorts.find((cohort) => cohort.openTaQueueItems > 0)
    ?? queueCourse?.cohorts[0]
  const nextOfficeHours = officeHours[0]

  const openCourseTab = (
    course: TeachingCourseSummary | undefined,
    tab: 'questions' | 'resources' | 'office-hours' | 'people',
    cohortId?: string,
    sessionId?: string,
  ) => {
    if (!serverId || !course) return
    navigate(courseContextPath(serverId, course, tab, cohortId, sessionId))
  }

  return (
    <section className="teaching-page">
      <header>
        <div>
          <h1>Good evening, {user?.displayName ?? 'Instructor'}</h1>
          <p>Teaching overview</p>
          <span>Cross-course actions for courses you instruct</span>
        </div>
        {page.servers.length > 1 ? (
          <label className="teaching-server-select">
            <span>Study Server</span>
            <select
              value={serverId ?? ''}
              onChange={(event) => page.setSelectedServerId(event.target.value)}
            >
              {page.servers.map((server) => (
                <option value={server.id} key={server.id}>{server.name}</option>
              ))}
            </select>
          </label>
        ) : null}
      </header>

      {page.isLoading ? <p className="teaching-state" role="status">Loading dashboard...</p> : null}
      {page.error ? <p className="inline-error">{page.error}</p> : null}
      {officeHoursError ? <p className="inline-error">{officeHoursError}</p> : null}

      {dashboard ? (
        <>
          <div className="teaching-metrics">
            <article>
              <span className="purple"><CircleHelp /></span>
              <div>
                <strong>{dashboard.unansweredSupportQuestions}</strong>
                <p>Open questions<br />across {courses.length} {courses.length === 1 ? 'course' : 'courses'}</p>
              </div>
              <button
                type="button"
                disabled={!openQuestionCourse}
                onClick={() => openCourseTab(openQuestionCourse, 'questions')}
              >
                View
              </button>
            </article>
            <article>
              <span className="green"><ClipboardCheck /></span>
              <div>
                <strong>{dashboard.repeatedQuestionGroups}</strong>
                <p>FAQ candidates<br />awaiting approval</p>
              </div>
              <button
                type="button"
                disabled={!faqCourse}
                onClick={() => openCourseTab(faqCourse, 'questions')}
              >
                Review
              </button>
            </article>
            <article>
              <span className="blue"><CalendarDays /></span>
              <div>
                <p>{nextOfficeHours?.session.status === 'LIVE' ? 'Office hours live' : 'Next Office Hours'}</p>
                <strong>{nextOfficeHours ? formatSessionTime(nextOfficeHours.session.startsAt) : 'Not scheduled'}</strong>
                <small>{nextOfficeHours?.course.title ?? 'Choose a course to schedule'}</small>
              </div>
              <button
                type="button"
                disabled={!nextOfficeHours && !firstCourse}
                aria-label={nextOfficeHours ? `Join ${nextOfficeHours.course.title} Office Hours` : 'Open Office Hours'}
                onClick={() => openCourseTab(
                  nextOfficeHours?.course ?? firstCourse,
                  'office-hours',
                  nextOfficeHours?.cohortId,
                  nextOfficeHours?.session.id,
                )}
              >
                {nextOfficeHours?.session.status === 'LIVE' ? 'Join' : 'Open'}
              </button>
            </article>
          </div>

          <div className="teaching-grid">
            <article>
              <h2>Courses you're teaching</h2>
              {courses.length === 0 ? <p className="teaching-empty">No assigned courses yet.</p> : null}
              {courses.map((course, index) => (
                <button
                  type="button"
                  key={course.courseId}
                  aria-label={`Open questions for ${course.title}`}
                  onClick={() => openCourseTab(course, 'questions')}
                >
                  <i style={{ background: COURSE_COLORS[index % COURSE_COLORS.length] }} />
                  <span>
                    <strong>
                      {course.title}
                      {course.cohorts[0] ? <b>{course.cohorts[0].name}</b> : null}
                    </strong>
                    <small>
                      {course.unansweredSupportQuestions} open questions · {course.repeatedQuestionGroups} FAQ · {course.openTaQueueItems} in TA queue
                    </small>
                  </span>
                  <ChevronRight />
                </button>
              ))}
            </article>
            <article>
              <h2>TA queue summary</h2>
              {courses.length === 0 ? <p className="teaching-empty">No course queues available.</p> : null}
              {courses.map((course, index) => (
                <p key={course.courseId}>
                  <i style={{ background: COURSE_COLORS[index % COURSE_COLORS.length] }} />
                  <strong>{course.title}</strong>
                  <span>{course.openTaQueueItems} waiting</span>
                  <small>{course.unansweredSupportQuestions} open</small>
                </p>
              ))}
              <button
                type="button"
                disabled={!queueCourse}
                onClick={() => openCourseTab(queueCourse, 'questions', queueCohort?.cohortId)}
              >
                View queues
              </button>
            </article>
          </div>

          <article className="teaching-ai-usage">
            <span><Sparkles /></span>
            <div>
              <h2>AI Study Assistant usage</h2>
              <p><b>{dashboard.planTier} plan</b>{used} / {limit} AI queries</p>
              <small>{dashboard.remainingAiInvocations} queries remaining this period</small>
            </div>
            <div className="teaching-usage-bar" aria-label={`${percent}% of AI quota used`}>
              <i style={{ width: `${percent}%` }} />
            </div>
            <strong>{percent}%</strong>
          </article>

          <nav className="teaching-shortcuts" aria-label="Course shortcuts">
            {[
              { Icon: CircleHelp, label: 'Questions', tab: 'questions' as const },
              { Icon: FileText, label: 'Resources', tab: 'resources' as const },
              { Icon: Clock, label: 'Office Hours', tab: 'office-hours' as const },
              { Icon: UsersRound, label: 'People', tab: 'people' as const },
            ].map(({ Icon, label, tab }) => (
              <button
                type="button"
                key={label}
                disabled={!firstCourse}
                onClick={() => openCourseTab(firstCourse, tab)}
              >
                <span><Icon /></span>
                <b>{label}</b>
                <small>{firstCourse?.title ?? 'No course available'}</small>
                <ChevronRight />
              </button>
            ))}
          </nav>
        </>
      ) : null}
    </section>
  )
}
