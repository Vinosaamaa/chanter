import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { cn } from '../../../lib/cn'
import { deleteStudyServer } from '../shell-api'
import { useAccessibleStudyServersQuery } from '../hooks/use-shell-queries'
import type { StudyServerSummary } from '../types'

import { SourceDeletionDialog } from '../../account-data/SourceDeletionDialog'
import { StudyServerIcon } from './StudyServerIcon'
import { studyServerIconStyle } from '../study-server-icon-style'

export function StudyServerPickerPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const serversQuery = useAccessibleStudyServersQuery()
  const [pendingDelete, setPendingDelete] = useState<StudyServerSummary | null>(null)

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-app-bg">
      <header className="flex flex-wrap items-start justify-between gap-4 border-b border-app-border px-6 py-6">
        <div>
          <h1 className="text-2xl font-semibold text-app-text">Your Study Servers</h1>
          <p className="mt-1 max-w-2xl text-sm text-app-muted">
            Select a Study Server to continue studying and collaborating with your community.
          </p>
        </div>
        <Link
          to="/app/onboarding/create-study-server"
          className="v2-primary-button min-h-11"
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
                  setPendingDelete(server)
                }}
              />
            ))}
          </div>
        )}
      </div>

      {pendingDelete ? (
        <SourceDeletionDialog
          kind="STUDY_SERVER"
          targetId={pendingDelete.id}
          targetName={pendingDelete.name}
          submit={signal => deleteStudyServer(pendingDelete.id, signal)}
          onClose={() => setPendingDelete(null)}
          onAccepted={request => {
            setPendingDelete(null)
            void queryClient.invalidateQueries({ queryKey: ['study-servers'] })
            void navigate(`/app/deletions/${request.jobId}`)
          }}
        />
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
  const { color } = studyServerIconStyle(server.id)

  return (
    <article
      className={cn(
        'group relative flex flex-col overflow-hidden rounded-2xl border border-app-border bg-app-surface',
        'transition hover:border-app-accent/40 hover:shadow-lg hover:shadow-black/10',
      )}
    >
      <div className="h-1.5 w-full" style={{ backgroundColor: color }} />
      <div className="flex flex-1 flex-col p-5">
        <div className="flex items-start gap-3">
          <StudyServerIcon serverId={server.id} size="sm" />
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
            className="v2-primary-button min-h-11"
          >
            Open Study Server
          </Link>
          {server.owner ? (
            <button
              type="button"
              onClick={onDelete}
              className="v2-outline-button min-h-11"
            >
              Delete
            </button>
          ) : null}
        </div>
      </div>
    </article>
  )
}
