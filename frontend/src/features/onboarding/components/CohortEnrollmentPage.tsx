import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { Button } from '../../../components/ui/button'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { courseChannelPath } from '../../shell/shell-routes'

import { useCohortEnrollment } from '../hooks/use-cohort-enrollment'

export function CohortEnrollmentPage() {
  const { serverId, courseId } = useParams()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const course = navigationQuery.data?.courses.find((item) => item.id === courseId)
  const [selectedCohortId, setSelectedCohortId] = useState('')
  const cohort =
    course?.cohorts.find((item) => item.id === selectedCohortId) ?? course?.cohorts[0]
  const enrollment = useCohortEnrollment(cohort?.id ?? '')

  if (!serverId || !courseId) {
    return null
  }

  if (navigationQuery.isLoading) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Loading enrollment…
      </section>
    )
  }

  if (navigationQuery.isError) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-red-300">
        Could not load enrollment for this Study Server.
      </section>
    )
  }

  if (!course || !cohort) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Course not found on this Study Server.
      </section>
    )
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-app-bg">
      <header className="border-b border-app-border px-6 py-5">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Cohort enrollment
        </p>
        <h1 className="mt-2 text-2xl font-semibold text-app-text">{course.title}</h1>
        {course.cohorts.length > 1 ? (
          <label className="mt-2 flex flex-col gap-1 text-xs text-app-muted">
            Cohort
            <select
              value={cohort.id}
              onChange={(event) => setSelectedCohortId(event.target.value)}
              className="max-w-xs rounded-lg border border-app-border bg-app-bg px-3 py-2 text-sm text-app-text"
            >
              {course.cohorts.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <p className="mt-1 text-sm text-app-muted">{cohort.name}</p>
        )}
      </header>

      <div className="grid flex-1 gap-6 p-6 lg:grid-cols-2">
        <form
          className="rounded-xl border border-app-border bg-app-surface p-5"
          onSubmit={(event) => {
            event.preventDefault()
            void enrollment.enroll()
          }}
        >
          <h2 className="text-sm font-semibold text-app-text">Enroll learner</h2>
          <p className="mt-1 text-xs text-app-muted">
            Paste the learner&apos;s user id (from registration or the dev demo personas panel).
          </p>
          <label className="mt-4 flex flex-col gap-1 text-xs text-app-muted">
            Learner user id
            <input
              value={enrollment.learnerUserId}
              onChange={(event) => enrollment.setLearnerUserId(event.target.value)}
              disabled={enrollment.isSubmitting}
              placeholder="UUID"
              className="rounded-lg border border-app-border bg-app-bg px-3 py-2 text-sm text-app-text"
            />
          </label>

          {enrollment.error ? (
            <p role="alert" className="mt-3 text-sm text-red-300">
              {enrollment.error}
            </p>
          ) : null}

          {enrollment.successMessage ? (
            <p role="status" className="mt-3 text-sm text-emerald-200">
              {enrollment.successMessage}
            </p>
          ) : null}

          <div className="mt-4 flex flex-wrap gap-2">
            <Button type="submit" disabled={enrollment.isSubmitting}>
              {enrollment.isSubmitting ? 'Enrolling…' : 'Enroll learner'}
            </Button>
            <Link
              to={`/app/servers/${serverId}/home`}
              className="rounded-lg border border-app-border px-4 py-2 text-sm text-app-muted hover:bg-app-elevated hover:text-app-text"
            >
              Back to home
            </Link>
          </div>
        </form>

        <article className="rounded-xl border border-app-border bg-app-surface p-5">
          <h2 className="text-sm font-semibold text-app-text">Channel access preview</h2>
          <p className="mt-1 text-xs text-app-muted">
            After enrollment, the learner can access these course channels (TA assignment ships in a
            later slice).
          </p>
          <ul className="mt-4 space-y-2 text-sm">
            {course.channels.map((channel) => (
              <li
                key={channel.id}
                className="flex items-center justify-between rounded-lg border border-app-border/70 px-3 py-2"
              >
                <span className="text-app-text">
                  {channel.kind === 'VOICE' ? '>' : '#'}
                  {channel.name}
                </span>
                <Link
                  to={courseChannelPath(serverId, channel.id)}
                  className="text-xs text-app-accent hover:underline"
                >
                  Preview
                </Link>
              </li>
            ))}
          </ul>
        </article>
      </div>
    </section>
  )
}
