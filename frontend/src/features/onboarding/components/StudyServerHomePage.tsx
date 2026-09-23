import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { formatUserFacingApiError, isUnauthorizedApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { courseChannelPath } from '../../shell/shell-routes'
import { StudyServerIcon } from '../../shell/components/StudyServerIcon'

import { createCourse } from '../onboarding-api'

function courseAccent(title: string): string {
  const palette = ['#7c6cff', '#3ecf8e', '#4da3ff', '#f5a623', '#ff6b8a']
  let hash = 0
  for (const char of title) {
    hash = (hash + char.charCodeAt(0)) % palette.length
  }
  return palette[hash] ?? palette[0]
}

export function StudyServerHomePage() {
  const { serverId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const [courseTitle, setCourseTitle] = useState('')
  const [cohortName, setCohortName] = useState('')
  const [isCreatingCourse, setIsCreatingCourse] = useState(false)
  const [courseError, setCourseError] = useState<string | null>(null)
  const [courseMessage, setCourseMessage] = useState<string | null>(null)

  if (!serverId) {
    return null
  }

  const onCreateCourse = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const title = courseTitle.trim()
    const cohort = cohortName.trim()
    if (!title || !cohort) {
      setCourseError('Course title and cohort name are required.')
      return
    }

    setIsCreatingCourse(true)
    setCourseError(null)
    setCourseMessage(null)

    try {
      await createCourse(serverId, { title, cohortName: cohort })
      setCourseTitle('')
      setCohortName('')
      setCourseMessage(`Created ${title} (${cohort}).`)
      await queryClient.invalidateQueries({ queryKey: ['study-server-navigation'] })
    } catch (caught) {
      if (isUnauthorizedApiError(caught)) {
        clearSession()
        navigate('/sign-in', {
          replace: true,
          state: { from: `/app/servers/${serverId}/home` },
        })
        return
      }
      setCourseError(formatUserFacingApiError(caught, 'Unable to create course.'))
    } finally {
      setIsCreatingCourse(false)
    }
  }

  const navigation = navigationQuery.data
  const canManage = navigation?.capabilities.canCreateCourse ?? false

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-app-bg">
      <header className="border-b border-app-border px-6 py-6">
        <div className="flex flex-wrap items-start gap-4">
          <StudyServerIcon serverId={serverId} size="md" />
          <div className="min-w-0 flex-1">
            <h1 className="text-2xl font-semibold text-app-text">
              {navigation?.studyServerName ?? 'Study Server'}
            </h1>
            <p className="mt-1 max-w-2xl text-sm text-app-muted">
              {canManage
                ? 'Create courses and open enrollment for your cohorts.'
                : 'Your enrolled courses on this Study Server.'}
            </p>
          </div>
        </div>
      </header>

      <div className="flex-1 space-y-6 p-6">
        {navigationQuery.isLoading && <p className="text-sm text-app-muted">Loading courses…</p>}

        {navigationQuery.isError && (
          <p role="alert" className="inline-error">
            Could not load courses for this Study Server.
          </p>
        )}

        {courseMessage ? (
          <p
            role="status"
            className="inline-success"
          >
            {courseMessage}
          </p>
        ) : null}

        {navigation && navigation.courses.length > 0 ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {navigation.courses.map((course) => {
              const firstTextChannel =
                course.channels.find((channel) => channel.kind === 'TEXT') ?? course.channels[0]
              const accent = courseAccent(course.title)

              return (
                <article
                  key={course.id}
                  className="overflow-hidden rounded-xl border border-app-border bg-app-surface"
                >
                  <div className="h-1.5" style={{ background: accent }} />
                  <div className="space-y-3 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <h2 className="text-lg font-semibold text-app-text">{course.title}</h2>
                        {course.cohorts[0] ? (
                          <p className="mt-1 text-sm text-app-muted">{course.cohorts[0].name}</p>
                        ) : null}
                      </div>
                    </div>
                    <p className="text-xs text-app-muted">
                      {course.channels.length} channel{course.channels.length === 1 ? '' : 's'}
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {firstTextChannel ? (
                        <Link
                          to={courseChannelPath(serverId, firstTextChannel.id)}
                          className="v2-outline-button"
                        >
                          Open #{firstTextChannel.name}
                        </Link>
                      ) : null}
                      {canManage && course.cohorts[0] ? (
                        <Link
                          to={`/app/servers/${serverId}/courses/${course.id}/enrollment`}
                          className="v2-primary-button"
                        >
                          Manage enrollment
                        </Link>
                      ) : null}
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
        ) : (
          !navigationQuery.isLoading &&
          navigation && (
            <p className="text-sm text-app-muted">
              {canManage
                ? 'No courses yet. Create your first course below.'
                : 'You are not enrolled in any courses on this Study Server yet.'}
            </p>
          )
        )}

        {canManage ? (
          <form
            onSubmit={onCreateCourse}
            className="max-w-xl rounded-xl border border-app-border bg-app-surface p-5"
          >
            <h2 className="text-sm font-semibold text-app-text">Create course + cohort</h2>
            <p className="mt-1 text-xs text-app-muted">
              Adds #announcements, #questions, and #resources channels for the cohort.
            </p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <label className="flex flex-col gap-1 text-xs text-app-muted">
                Course title
                <input
                  value={courseTitle}
                  onChange={(event) => setCourseTitle(event.target.value)}
                  required
                  disabled={isCreatingCourse}
                  className="min-h-11 rounded-lg border border-app-border bg-app-bg px-3 py-2 text-base text-app-text"
                />
              </label>
              <label className="flex flex-col gap-1 text-xs text-app-muted">
                Cohort name
                <input
                  value={cohortName}
                  onChange={(event) => setCohortName(event.target.value)}
                  required
                  disabled={isCreatingCourse}
                  placeholder="e.g. September cohort"
                  className="min-h-11 rounded-lg border border-app-border bg-app-bg px-3 py-2 text-base text-app-text"
                />
              </label>
            </div>
            {courseError ? (
              <p role="alert" className="inline-error mt-3">
                {courseError}
              </p>
            ) : null}
            <button type="submit" className="v2-primary-button mt-4" disabled={isCreatingCourse}>
              {isCreatingCourse ? 'Creating…' : 'Create course'}
            </button>
          </form>
        ) : null}
      </div>
    </section>
  )
}
