import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { Button } from '../../../components/ui/button'
import { cn } from '../../../lib/cn'
import { formatUserFacingApiError, isUnauthorizedApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'
import { deleteStudyServer } from '../shell-api'
import { useAccessibleStudyServersQuery } from '../hooks/use-shell-queries'
import type { StudyServerSummary } from '../types'

export function StudyServerPickerPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const serversQuery = useAccessibleStudyServersQuery()
  const [pendingDelete, setPendingDelete] = useState<StudyServerSummary | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const onConfirmDelete = async () => {
    if (!pendingDelete) {
      return
    }

    setIsDeleting(true)
    setDeleteError(null)

    try {
      await deleteStudyServer(pendingDelete.id)
      await queryClient.invalidateQueries({ queryKey: ['study-servers'] })
      setPendingDelete(null)
    } catch (caught) {
      if (isUnauthorizedApiError(caught)) {
        clearSession()
        navigate('/sign-in', { replace: true, state: { from: '/app' } })
        return
      }
      setDeleteError(formatUserFacingApiError(caught, 'Unable to delete Study Server.'))
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-app-bg">
      <header className="flex flex-wrap items-start justify-between gap-4 border-b border-app-border px-6 py-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
            Study Server
          </p>
          <h1 className="mt-2 text-2xl font-semibold text-app-text">Your Study Servers</h1>
          <p className="mt-1 max-w-2xl text-sm text-app-muted">
            Select a server to continue studying and collaborating with your community.
          </p>
        </div>
        <Link
          to="/app/onboarding/create-study-server"
          className="inline-flex items-center justify-center rounded-md bg-app-accent px-4 py-2 text-sm font-medium text-white hover:bg-app-accent-hover"
        >
          + Create Study Server
        </Link>
      </header>

      <div className="flex-1 p-6">
        {serversQuery.isLoading && (
          <p className="text-sm text-app-muted">Loading your Study Servers…</p>
        )}

        {serversQuery.isError && (
          <p role="alert" className="text-sm text-red-300">
            Could not load your Study Servers.
          </p>
        )}

        {serversQuery.data && serversQuery.data.length === 0 && (
          <div className="mx-auto flex max-w-lg flex-col items-center rounded-2xl border border-dashed border-app-border bg-app-surface px-8 py-12 text-center">
            <p className="text-lg font-semibold text-app-text">No Study Servers yet</p>
            <p className="mt-2 text-sm text-app-muted">
              Create your first learning community to add courses, channels, and members.
            </p>
            <Link
              to="/app/onboarding/create-study-server"
              className="mt-6 inline-flex items-center justify-center rounded-md bg-app-accent px-4 py-2 text-sm font-medium text-white hover:bg-app-accent-hover"
            >
              Create Study Server
            </Link>
          </div>
        )}

        {serversQuery.data && serversQuery.data.length > 0 && (
          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {serversQuery.data.map((server) => (
              <StudyServerCard
                key={server.id}
                server={server}
                onDelete={() => {
                  setDeleteError(null)
                  setPendingDelete(server)
                }}
              />
            ))}
          </div>
        )}
      </div>

      {pendingDelete ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-study-server-title"
        >
          <div className="w-full max-w-md rounded-xl border border-app-border bg-app-elevated p-5 shadow-xl">
            <h2 id="delete-study-server-title" className="text-lg font-semibold text-app-text">
              Delete {pendingDelete.name}?
            </h2>
            <p className="mt-2 text-sm text-app-muted">
              This permanently removes the Study Server, its courses, channels, and enrollments.
              This action cannot be undone.
            </p>
            {deleteError ? (
              <p role="alert" className="mt-3 text-sm text-red-300">
                {deleteError}
              </p>
            ) : null}
            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="secondary"
                disabled={isDeleting}
                onClick={() => setPendingDelete(null)}
              >
                Cancel
              </Button>
              <Button
                type="button"
                className="bg-red-600 hover:bg-red-500"
                disabled={isDeleting}
                onClick={onConfirmDelete}
              >
                {isDeleting ? 'Deleting…' : 'Delete Study Server'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  )
}

function StudyServerCard({
  server,
  onDelete,
}: {
  server: StudyServerSummary
  onDelete: () => void
}) {
  const accent = serverAccent(server.name)

  return (
    <article
      className={cn(
        'group relative flex flex-col overflow-hidden rounded-2xl border border-app-border bg-app-surface',
        'transition hover:border-app-accent/40 hover:shadow-lg hover:shadow-black/20',
      )}
    >
      <div className={cn('h-1.5 w-full bg-gradient-to-r', accent.bar)} />
      <div className="flex flex-1 flex-col p-5">
        <div className="flex items-start gap-3">
          <span
            className={cn(
              'flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-sm font-semibold text-white',
              accent.icon,
            )}
          >
            {initials(server.name)}
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-lg font-semibold text-app-text">{server.name}</h2>
            <p className="mt-1 line-clamp-2 text-sm text-app-muted">
              {server.owner
                ? 'Your learning community — manage courses, channels, and members.'
                : 'A shared learning community you belong to.'}
            </p>
          </div>
        </div>

        <div className="mt-5 flex items-center gap-4 text-xs text-app-muted">
          <span>
            {server.courseCount} course{server.courseCount === 1 ? '' : 's'}
          </span>
          <span>
            {server.memberCount} member{server.memberCount === 1 ? '' : 's'}
          </span>
        </div>

        <div className="mt-5 flex flex-wrap gap-2">
          <Link
            to={`/app/servers/${server.id}/home`}
            className="rounded-lg bg-app-accent px-3 py-1.5 text-sm font-medium text-white hover:opacity-90"
          >
            Open server
          </Link>
          {server.owner ? (
            <button
              type="button"
              onClick={onDelete}
              className="rounded-lg border border-red-500/40 px-3 py-1.5 text-sm text-red-200 hover:bg-red-500/10"
            >
              Delete
            </button>
          ) : null}
        </div>
      </div>
    </article>
  )
}

function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) {
    return '?'
  }
  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase()
  }
  return `${words[0][0]}${words[1][0]}`.toUpperCase()
}

function serverAccent(name: string): { bar: string; icon: string } {
  const palette = [
    { bar: 'from-violet-500 to-fuchsia-500', icon: 'bg-violet-600' },
    { bar: 'from-emerald-500 to-teal-500', icon: 'bg-emerald-600' },
    { bar: 'from-sky-500 to-blue-600', icon: 'bg-sky-600' },
    { bar: 'from-amber-500 to-orange-500', icon: 'bg-amber-600' },
  ]
  const index = Math.abs(hashString(name)) % palette.length
  return palette[index]
}

function hashString(value: string): number {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) | 0
  }
  return hash
}
