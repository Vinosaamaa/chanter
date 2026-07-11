import { useEffect, useMemo, useRef, useState, type RefObject } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { cn } from '../../../lib/cn'
import { formatUserFacingApiError } from '../../../lib/format-api-error'
import { readActiveStudyServerId } from '../../../lib/last-active-study-server'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import {
  courseChannelPath,
  supportOperationPath,
} from '../../shell/shell-routes'
import { useGlobalSearch } from '../hooks/use-global-search'
import { reindexStudyServer, searchStudyServer } from '../global-search-api'
import type { GlobalSearchHit } from '../global-search-types'

type ContentTypeFilter = 'all' | 'RESOURCE' | 'FAQ'

function hitDestination(serverId: string, hit: GlobalSearchHit, resourcesChannelId: string | null): string {
  if (hit.documentType === 'FAQ') {
    return supportOperationPath(serverId, hit.courseId, 'faq-approval')
  }

  if (resourcesChannelId) {
    return courseChannelPath(serverId, resourcesChannelId)
  }

  return `/app/servers/${serverId}/home`
}

function focusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(
    container.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  )
}

function GlobalSearchOverlayPanel({
  onClose,
  panelRef,
}: {
  onClose: () => void
  panelRef: RefObject<HTMLElement | null>
}) {
  const { serverId: routeServerId } = useParams()
  const serverId = routeServerId ?? readActiveStudyServerId() ?? undefined
  const navigate = useNavigate()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<GlobalSearchHit[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isSearching, setIsSearching] = useState(false)
  const [isReindexing, setIsReindexing] = useState(false)
  const [reindexMessage, setReindexMessage] = useState<string | null>(null)
  const [contentTypeFilter, setContentTypeFilter] = useState<ContentTypeFilter>('all')
  const [courseFilter, setCourseFilter] = useState<string>('all')

  const trimmedQuery = query.trim()

  const handleQueryChange = (value: string) => {
    setQuery(value)
    if (value.trim().length < 2) {
      setResults([])
      setError(null)
      setIsSearching(false)
    }
  }

  useEffect(() => {
    if (!serverId || trimmedQuery.length < 2) {
      return
    }

    let cancelled = false
    const handle = window.setTimeout(() => {
      setIsSearching(true)
      setError(null)
      void searchStudyServer(serverId, trimmedQuery)
        .then((response) => {
          if (!cancelled) {
            setResults(response.results)
          }
        })
        .catch((caught) => {
          if (!cancelled) {
            setResults([])
            setError(formatUserFacingApiError(caught, 'Search failed.'))
          }
        })
        .finally(() => {
          if (!cancelled) {
            setIsSearching(false)
          }
        })
    }, 250)

    return () => {
      cancelled = true
      window.clearTimeout(handle)
    }
  }, [trimmedQuery, serverId])

  const canManage = navigationQuery.data?.canViewFullCatalog ?? false
  const courseIds = useMemo(
    () => new Set((navigationQuery.data?.courses ?? []).map((course) => course.id)),
    [navigationQuery.data?.courses],
  )
  const activeCourseFilter =
    courseFilter !== 'all' && !courseIds.has(courseFilter) ? 'all' : courseFilter

  const groupedResults = useMemo(() => {
    const base = (serverId && trimmedQuery.length >= 2 ? results : []).filter((hit) => {
      if (contentTypeFilter !== 'all' && hit.documentType !== contentTypeFilter) {
        return false
      }
      if (activeCourseFilter !== 'all' && hit.courseId !== activeCourseFilter) {
        return false
      }
      return true
    })

    const resourceResults: GlobalSearchHit[] = []
    const faqResults: GlobalSearchHit[] = []
    for (const hit of base) {
      if (hit.documentType === 'RESOURCE') {
        resourceResults.push(hit)
      } else if (hit.documentType === 'FAQ') {
        faqResults.push(hit)
      }
    }

    return { all: base, resourceResults, faqResults }
  }, [activeCourseFilter, contentTypeFilter, results, serverId, trimmedQuery])

  const courseLookup = useMemo(() => {
    const resourcesChannelByCourseId = new Map<string, string>()
    for (const course of navigationQuery.data?.courses ?? []) {
      const resourcesChannel = course.channels.find((channel) => channel.name === 'resources')
      if (resourcesChannel) {
        resourcesChannelByCourseId.set(course.id, resourcesChannel.id)
      }
    }
    return { resourcesChannelByCourseId }
  }, [navigationQuery.data?.courses])

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
      ref={panelRef}
      role="dialog"
      aria-modal="true"
      aria-label="Global search"
      className="relative z-10 w-full max-w-2xl overflow-hidden rounded-2xl border border-app-border bg-app-surface shadow-2xl"
    >
      <header className="border-b border-app-border px-4 py-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.12em] text-app-accent">
              Search
            </p>
            <h2 className="text-sm font-semibold text-app-text">Course knowledge</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-app-border px-2 py-1 text-xs text-app-muted hover:text-app-text"
          >
            esc
          </button>
        </div>
        <label className="mt-3 flex flex-col gap-1 text-xs text-app-muted">
          Search resources and approved FAQs
          <input
            autoFocus
            value={query}
            onChange={(event) => handleQueryChange(event.target.value)}
            placeholder="Search resources and approved FAQs"
            className="rounded-lg border border-app-border bg-app-bg px-3 py-2 text-sm text-app-text"
          />
        </label>
        <div className="mt-3 flex flex-wrap gap-2">
          <FilterSelect
            label="Course"
            value={activeCourseFilter}
            onChange={setCourseFilter}
            options={[
              { value: 'all', label: 'All courses' },
              ...(navigationQuery.data?.courses.map((course) => ({
                value: course.id,
                label: course.title,
              })) ?? []),
            ]}
          />
          <FilterSelect
            label="Content"
            value={contentTypeFilter}
            onChange={(value) => setContentTypeFilter(value as ContentTypeFilter)}
            options={[
              { value: 'all', label: 'All types' },
              { value: 'RESOURCE', label: 'Resources' },
              { value: 'FAQ', label: 'FAQs' },
            ]}
          />
        </div>
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

        {serverId && trimmedQuery.length >= 2 && isSearching ? (
          <p className="px-3 py-6 text-sm text-app-muted">Searching…</p>
        ) : null}

        {serverId && trimmedQuery.length >= 2 && error ? (
          <p role="alert" className="px-3 py-4 text-sm text-red-300">
            {error}
          </p>
        ) : null}

        {reindexMessage ? (
          <p role="status" className="px-3 py-2 text-sm text-emerald-200">
            {reindexMessage}
          </p>
        ) : null}

        {!isSearching && groupedResults.all.length === 0 && trimmedQuery.length >= 2 && !error ? (
          <p className="px-3 py-6 text-sm text-app-muted">No matching resources or FAQs.</p>
        ) : null}

        <SearchResultSection
          title="Course resources"
          count={groupedResults.resourceResults.length}
          hits={groupedResults.resourceResults}
          serverId={serverId}
          courseLookup={courseLookup}
          onNavigate={(destination) => {
            onClose()
            navigate(destination)
          }}
        />
        <SearchResultSection
          title="Approved FAQs"
          count={groupedResults.faqResults.length}
          hits={groupedResults.faqResults}
          serverId={serverId}
          courseLookup={courseLookup}
          onNavigate={(destination) => {
            onClose()
            navigate(destination)
          }}
        />
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
          Showing enrollment-scoped results only. ⌘K search · esc close
        </footer>
      ) : null}
    </section>
  )
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <label className="text-[11px] text-app-muted">
      {label}
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="ml-1 rounded-md border border-app-border bg-app-bg px-2 py-1 text-xs text-app-text"
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  )
}

function SearchResultSection({
  title,
  count,
  hits,
  serverId,
  courseLookup,
  onNavigate,
}: {
  title: string
  count: number
  hits: GlobalSearchHit[]
  serverId: string | undefined
  courseLookup: {
    resourcesChannelByCourseId: Map<string, string>
  }
  onNavigate: (destination: string) => void
}) {
  if (hits.length === 0) {
    return null
  }

  return (
    <section className="px-2 py-2">
      <div className="flex items-center justify-between px-2 py-1">
        <h3 className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">{title}</h3>
        <span className="rounded-full bg-app-elevated px-2 py-0.5 text-[10px] text-app-muted">{count}</span>
      </div>
      <ul className="flex flex-col gap-1">
        {hits.map((hit) => {
          const resourcesChannelId = courseLookup.resourcesChannelByCourseId.get(hit.courseId) ?? null
          const destination = hitDestination(serverId ?? '', hit, resourcesChannelId)

          return (
            <li key={`${hit.documentType}-${hit.sourceId}`}>
              <button
                type="button"
                className={cn(
                  'flex w-full flex-col rounded-lg px-3 py-2 text-left transition-colors hover:bg-app-elevated',
                )}
                onClick={() => onNavigate(destination)}
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
    </section>
  )
}

export function GlobalSearchOverlay() {
  const { isOpen, closeSearch } = useGlobalSearch()
  const panelRef = useRef<HTMLElement>(null)
  const previouslyFocusedRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!isOpen) {
      return
    }

    previouslyFocusedRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Tab' || !panelRef.current) {
        return
      }

      const focusable = focusableElements(panelRef.current)
      if (focusable.length === 0) {
        return
      }

      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      const active = document.activeElement

      if (event.shiftKey && active === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)

    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previouslyFocusedRef.current?.focus()
    }
  }, [isOpen])

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
      <GlobalSearchOverlayPanel onClose={closeSearch} panelRef={panelRef} />
    </div>
  )
}
