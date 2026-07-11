import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { Button } from '../../../components/ui/button'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { courseChannelPath } from '../../shell/shell-routes'

import { useCohortEnrollments, useCohortInvite } from '../hooks/use-cohort-enrollments'
import { useCohortEnrollment } from '../hooks/use-cohort-enrollment'

const pageSize = 8

function cohortInviteUrl(cohortId: string, inviteCode: string): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'https://app.chanter.local'
  const params = new URLSearchParams({ cohort: cohortId, invite: inviteCode })
  return `${origin}/sign-in?${params.toString()}`
}

function formatLearnerLabel(userId: string): string {
  return userId.length > 12 ? `${userId.slice(0, 8)}…` : userId
}

export function CohortEnrollmentPage() {
  const { serverId, courseId } = useParams()
  const queryClient = useQueryClient()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const course = navigationQuery.data?.courses.find((item) => item.id === courseId)
  const [selectedCohortId, setSelectedCohortId] = useState('')
  const cohort =
    course?.cohorts.find((item) => item.id === selectedCohortId) ?? course?.cohorts[0]
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [page, setPage] = useState(1)
  const enrollment = useCohortEnrollment(cohort?.id ?? '')
  const inviteQuery = useCohortInvite(cohort?.id)
  const enrollmentsQuery = useCohortEnrollments(cohort?.id, {
    limit: pageSize,
    offset: (page - 1) * pageSize,
    search: debouncedSearch || undefined,
  })
  const [copyMessage, setCopyMessage] = useState<string | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [search])

  const totalCount = enrollmentsQuery.data?.totalCount ?? 0
  const pageRows = enrollmentsQuery.data?.enrollments ?? []
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize))
  const currentPage = Math.min(page, totalPages)

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

  const inviteUrl =
    inviteQuery.data != null
      ? cohortInviteUrl(inviteQuery.data.cohortId, inviteQuery.data.inviteCode)
      : null

  const onCopyInvite = async () => {
    if (!inviteUrl) {
      return
    }
    try {
      await navigator.clipboard.writeText(inviteUrl)
      setCopyMessage('Invite link copied.')
    } catch {
      setCopyMessage('Unable to copy invite link.')
    }
  }

  const onEnroll = async () => {
    const enrolled = await enrollment.enroll()
    if (enrolled && cohort) {
      await queryClient.invalidateQueries({ queryKey: ['cohort-enrollments', cohort.id] })
    }
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-app-bg">
      <header className="border-b border-app-border px-6 py-5">
        <p className="text-xs text-app-muted">
          <Link to={`/app/servers/${serverId}/home`} className="hover:text-app-text">
            Study Server home
          </Link>
          <span aria-hidden> / </span>
          <span>{course.title}</span>
          <span aria-hidden> / </span>
          <span>Enrollment</span>
        </p>
        <h1 className="mt-2 text-2xl font-semibold text-app-text">{course.title}</h1>
        {course.cohorts.length > 1 ? (
          <label className="mt-2 flex flex-col gap-1 text-xs text-app-muted">
            Cohort
            <select
              value={cohort.id}
              onChange={(event) => {
                setSelectedCohortId(event.target.value)
                enrollment.reset()
                setPage(1)
                setCopyMessage(null)
              }}
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

      <div className="grid flex-1 gap-6 p-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-sm font-semibold text-app-text">Learners ({totalCount})</h2>
            <label className="flex w-full max-w-xs flex-col gap-1 text-xs text-app-muted">
              Search learners
              <input
                value={search}
                onChange={(event) => {
                  setSearch(event.target.value)
                  setPage(1)
                }}
                placeholder="Search by user id…"
                className="rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text"
              />
            </label>
          </div>

          <div className="overflow-hidden rounded-xl border border-app-border bg-app-surface">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-app-border bg-app-elevated text-xs uppercase tracking-wide text-app-muted">
                <tr>
                  <th className="px-4 py-3 font-medium">Learner</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Assigned TA</th>
                  <th className="px-4 py-3 font-medium">Enrolled</th>
                </tr>
              </thead>
              <tbody>
                {enrollmentsQuery.isLoading ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-6 text-app-muted">
                      Loading learners…
                    </td>
                  </tr>
                ) : enrollmentsQuery.isError ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-6 text-red-300">
                      Could not load enrollments.
                    </td>
                  </tr>
                ) : pageRows.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-6 text-app-muted">
                      No learners enrolled yet. Use the invite link or enroll manually.
                    </td>
                  </tr>
                ) : (
                  pageRows.map((row) => (
                    <tr key={row.learnerUserId} className="border-t border-app-border/70">
                      <td className="px-4 py-3 font-medium text-app-text">
                        {formatLearnerLabel(row.learnerUserId)}
                      </td>
                      <td className="px-4 py-3 text-emerald-300">Enrolled</td>
                      <td className="px-4 py-3 text-app-muted">Unassigned</td>
                      <td className="px-4 py-3 text-app-muted">
                        {new Date(row.enrolledAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {totalCount > pageSize ? (
            <div className="flex items-center justify-between text-xs text-app-muted">
              <span>
                {(currentPage - 1) * pageSize + 1}–
                {Math.min(currentPage * pageSize, totalCount)} of {totalCount}
              </span>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  disabled={currentPage <= 1}
                  onClick={() => setPage((value) => value - 1)}
                >
                  Previous
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={currentPage >= totalPages}
                  onClick={() => setPage((value) => value + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          ) : null}

          <form
            className="rounded-xl border border-app-border bg-app-surface p-5"
            onSubmit={(event) => {
              event.preventDefault()
              void onEnroll()
            }}
          >
            <h2 className="text-sm font-semibold text-app-text">Enroll learner manually</h2>
            <p className="mt-1 text-xs text-app-muted">
              Paste the learner&apos;s user id from registration or the dev demo personas panel.
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
            <Button type="submit" className="mt-4" disabled={enrollment.isSubmitting}>
              {enrollment.isSubmitting ? 'Enrolling…' : 'Enroll learner'}
            </Button>
          </form>
        </div>

        <aside className="space-y-4">
          <article className="rounded-xl border border-app-border bg-app-surface p-5">
            <h2 className="text-sm font-semibold text-app-text">Invite link</h2>
            <p className="mt-1 text-xs text-app-muted">
              Share this link so learners can sign in and join this cohort.
            </p>
            {inviteQuery.isLoading ? (
              <p className="mt-3 text-xs text-app-muted">Loading invite link…</p>
            ) : inviteQuery.isError || !inviteUrl ? (
              <p className="mt-3 text-xs text-red-300">Could not load invite link.</p>
            ) : (
              <>
                <p className="mt-3 break-all rounded-lg border border-app-border bg-app-bg px-3 py-2 text-xs text-app-text">
                  {inviteUrl}
                </p>
                {copyMessage ? (
                  <p role="status" className="mt-2 text-xs text-emerald-200">
                    {copyMessage}
                  </p>
                ) : null}
                <Button
                  type="button"
                  variant="secondary"
                  className="mt-3"
                  onClick={() => void onCopyInvite()}
                >
                  Copy invite link
                </Button>
              </>
            )}
          </article>

          <article className="rounded-xl border border-app-border bg-app-surface p-5">
            <h2 className="text-sm font-semibold text-app-text">Course channels access</h2>
            <p className="mt-1 text-xs text-app-muted">
              Enrolled learners can access these course channels.
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

          <p className="text-xs text-app-muted">
            TA assignment UI is shown for layout parity; assigning TAs requires a follow-up backend
            slice.
          </p>
        </aside>
      </div>
    </section>
  )
}
