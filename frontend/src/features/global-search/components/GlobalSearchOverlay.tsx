import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { cn } from '../../../lib/cn'
import { formatUserFacingApiError } from '../../../lib/format-api-error'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import {
  courseChannelPath,
  supportOperationPath,
} from '../../shell/shell-routes'
import { useGlobalSearch } from '../hooks/use-global-search'
import { reindexStudyServer, searchStudyServer } from '../global-search-api'
import type { GlobalSearchHit } from '../global-search-types'

function hitDestination(serverId: string, hit: GlobalSearchHit, resourcesChannelId: string | null): string {
  if (hit.documentType === 'FAQ') {
    return supportOperationPath(serverId, hit.courseId, 'faq-approval')
  }

  if (resourcesChannelId) {
    return courseChannelPath(serverId, resourcesChannelId)
  }

  return `/app/servers/${serverId}/home`
}

function GlobalSearchOverlayPanel({ onClose }: { onClose: () => void }) {
  const { serverId } = useParams()
  const navigate = useNavigate()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<GlobalSearchHit[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isSearching, setIsSearching] = useState(false)
  const [isReindexing, setIsReindexing] = useState(false)
  const [reindexMessage, setReindexMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!serverId) {
      return
    }

    const trimmed = query.trim()
    if (trimmed.length < 2) {
      return
    }

    const handle = window.setTimeout(() => {
      setIsSearching(true)
      setError(null)
      void searchStudyServer(serverId, trimmed)
        .then((response) => {
          setResults(response.results)
        })
        .catch((caught) => {
          setResults([])
          setError(formatUserFacingApiError(caught, 'Search failed.'))
        })
        .finally(() => {
          setIsSearching(false)
        })
    }, 250)

    return () => window.clearTimeout(handle)
  }, [query, serverId])

  const canManage = navigationQuery.data?.canViewFullCatalog ?? false
  const trimmedQuery = query.trim()
  const visibleResults = trimmedQuery.length >= 2 ? results : []

  const onReindex = async () => {
    if (!serverId) {
      return
    }

    setIsReindexing(true)
    setError(null)
    setReindexMessage(null)

    try {
      const response = await reindexStudyServer(serverId)
      setReindexMessage(`Indexed ${response.indexedDocuments} documents for this Study Server.`)
    } catch (caught) {
      setError(formatUserFacingApiError(caught, 'Unable to refresh the search index.'))
    } finally {
      setIsReindexing(false)
    }
  }

  return (
    <section
      role="dialog"
      aria-modal="true"
      aria-label="Global search"
      className="relative z-10 w-full max-w-2xl overflow-hidden rounded-2xl border border-app-border bg-app-surface shadow-2xl"
    >
      <header className="border-b border-app-border px-4 py-3">
        <label className="flex flex-col gap-1 text-xs text-app-muted">
          Search this Study Server
          <input
            autoFocus
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search resources and approved FAQs"
            className="rounded-lg border border-app-border bg-app-bg px-3 py-2 text-sm text-app-text"
          />
        </label>
        <p className="mt-2 text-[11px] text-app-muted">
          Results are limited to courses you can access. Press Esc to close.
        </p>
      </header>

      <div className="max-h-[28rem] overflow-y-auto p-2">
        {!serverId ? (
          <p className="px-3 py-6 text-sm text-app-muted">
            Open a Study Server to search its resources and FAQs.
          </p>
        ) : null}

        {serverId && trimmedQuery.length < 2 ? (
          <p className="px-3 py-6 text-sm text-app-muted">Type at least two characters to search.</p>
        ) : null}

        {isSearching ? <p className="px-3 py-6 text-sm text-app-muted">Searching…</p> : null}

        {error ? (
          <p role="alert" className="px-3 py-4 text-sm text-red-300">
            {error}
          </p>
        ) : null}

        {reindexMessage ? (
          <p role="status" className="px-3 py-2 text-sm text-emerald-200">
            {reindexMessage}
          </p>
        ) : null}

        {!isSearching && visibleResults.length === 0 && trimmedQuery.length >= 2 && !error ? (
          <p className="px-3 py-6 text-sm text-app-muted">No matching resources or FAQs.</p>
        ) : null}

        <ul className="flex flex-col gap-1">
          {visibleResults.map((hit) => {
            const course = navigationQuery.data?.courses.find((item) => item.id === hit.courseId)
            const resourcesChannel = course?.channels.find((channel) => channel.name === 'resources')
            const destination = hitDestination(serverId ?? '', hit, resourcesChannel?.id ?? null)

            return (
              <li key={`${hit.documentType}-${hit.sourceId}`}>
                <button
                  type="button"
                  className={cn(
                    'flex w-full flex-col rounded-lg px-3 py-2 text-left transition-colors hover:bg-app-elevated',
                  )}
                  onClick={() => {
                    onClose()
                    navigate(destination)
                  }}
                >
                  <span className="text-xs font-semibold uppercase tracking-[0.12em] text-app-accent">
                    {hit.documentType === 'RESOURCE' ? 'Resource' : 'FAQ'} · {hit.courseTitle}
                  </span>
                  <span className="mt-1 text-sm font-medium text-app-text">{hit.title}</span>
                  <span className="mt-1 text-xs text-app-muted">{hit.snippet}</span>
                </button>
              </li>
            )
          })}
        </ul>
      </div>

      {serverId && canManage ? (
        <footer className="flex items-center justify-between border-t border-app-border px-4 py-3 text-xs text-app-muted">
          <span>Instructors can refresh the search index after uploading new content.</span>
          <button
            type="button"
            disabled={isReindexing}
            onClick={() => void onReindex()}
            className="rounded-lg border border-app-border px-3 py-1.5 text-sm text-app-text hover:bg-app-elevated disabled:opacity-60"
          >
            {isReindexing ? 'Indexing…' : 'Refresh index'}
          </button>
        </footer>
      ) : null}

      {serverId && !canManage ? (
        <footer className="border-t border-app-border px-4 py-3 text-xs text-app-muted">
          Showing enrollment-scoped results only.
          {visibleResults.length === 0 && trimmedQuery.length >= 2 ? (
            <> Ask your instructor to refresh the search index if content is missing.</>
          ) : null}
        </footer>
      ) : null}
    </section>
  )
}

export function GlobalSearchOverlay() {
  const { isOpen, closeSearch } = useGlobalSearch()

  if (!isOpen) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/60 px-4 py-16">
      <button
        type="button"
        aria-label="Close search"
        className="absolute inset-0"
        onClick={closeSearch}
      />
      <GlobalSearchOverlayPanel onClose={closeSearch} />
    </div>
  )
}
