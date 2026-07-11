import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { Button } from '../../../components/ui/button'
import { cn } from '../../../lib/cn'
import { formatUserFacingApiError, isUnauthorizedApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { courseChannelPath } from '../../shell/shell-routes'
import { StudyServerIcon } from '../../shell/components/StudyServerIcon'

import { createCourse } from '../onboarding-api'

function readStoredDescription(serverId: string): string | null {
  try {
    return sessionStorage.getItem(`chanter:study-server-description:${serverId}`)
  } catch {
    return null
  }
}

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

  const storedDescription = useMemo(
    () => (serverId ? readStoredDescription(serverId) : null),
    [serverId],
  )

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
      await queryClient.invalidateQueries({ queryKey: ['study-server-navigation', serverId] })
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
  const canManage = navigation?.canViewFullCatalog ?? false

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-app-bg">
      <header className="border-b border-app-border px-6 py-6">
        <div className="flex flex-wrap items-start gap-4">
          <StudyServerIcon serverId={serverId} size="md" />
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
              Study Server home
            </p>
            <h1 className="mt-2 text-2xl font-semibold text-app-text">
              {navigation?.studyServerName ?? 'Study Server'}
            </h1>
            <p className="mt-1 max-w-2xl text-sm text-app-muted">
              {storedDescription ??
                (canManage
                  ? 'Create courses and open enrollment for your cohorts.'
                  : 'Your enrolled courses on this Study Server.')}
            </p>
          </div>
        </div>
      </header>

      <div className="flex-1 space-y-6 p-6">
        {navigationQuery.isLoading && <p className="text-sm text-app-muted">Loading courses…</p>}

        {navigationQuery.isError && (
          <p role="alert" className="text-sm text-red-300">
            Could not load courses for this Study Server.
          </p>
        )}

        {courseMessage ? (
          <p
            role="status"
            className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200"
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
                  <div className="h-20" style={{ background: `linear-gradient(135deg, ${accent}55, ${accent}22)` }} />
                  <div className="space-y-3 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <h2 className="text-lg font-semibold text-app-text">{course.title}</h2>
                        {course.cohorts[0] ? (
                          <p className="mt-1 text-sm text-app-muted">{course.cohorts[0].name}</p>
                        ) : null}
                      </div>
                      <span
                        className="rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-white shadow-sm ring-1 ring-black/20"
                        style={{ backgroundColor: accent }}
                      >
                        Course
                      </span>
                    </div>
                    <p className="text-xs text-app-muted">
                      {course.channels.length} channel{course.channels.length === 1 ? '' : 's'}
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {firstTextChannel ? (
                        <Link
                          to={courseChannelPath(serverId, firstTextChannel.id)}
                          className="rounded-lg border border-app-border px-3 py-1.5 text-sm text-app-text hover:bg-app-elevated"
                        >
                          Open #{firstTextChannel.name}
                        </Link>
                      ) : null}
                      {canManage && course.cohorts[0] ? (
                        <Link
                          to={`/app/servers/${serverId}/courses/${course.id}/enrollment`}
                          className={cn(
                            'rounded-lg bg-app-accent px-3 py-1.5 text-sm font-medium text-white hover:bg-app-accent-hover',
                          )}
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
                  className="rounded-lg border border-app-border bg-app-bg px-3 py-2 text-sm text-app-text"
                />
              </label>
              <label className="flex flex-col gap-1 text-xs text-app-muted">
                Cohort name
                <input
                  value={cohortName}
                  onChange={(event) => setCohortName(event.target.value)}
                  required
                  disabled={isCreatingCourse}
                  placeholder="e.g. March 2026"
                  className="rounded-lg border border-app-border bg-app-bg px-3 py-2 text-sm text-app-text"
                />
              </label>
            </div>
            {courseError ? (
              <p role="alert" className="mt-3 text-sm text-red-300">
                {courseError}
              </p>
            ) : null}
            <Button type="submit" className="mt-4" disabled={isCreatingCourse}>
              {isCreatingCourse ? 'Creating…' : 'Create course'}
            </Button>
          </form>
        ) : null}
      </div>
    </section>
  )
}
