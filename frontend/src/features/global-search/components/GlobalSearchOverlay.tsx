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
import { searchStudyServer } from '../global-search-api'
import type { GlobalSearchHit } from '../global-search-types'
import { v2CoursePath } from '../../v2-shell/v2-routes'

type ContentTypeFilter = 'all' | GlobalSearchHit['documentType']
type SearchDestinationVariant = 'legacy' | 'v2'

const contentSections = [
  { type: 'RESOURCE', title: 'Course resources', label: 'Resource' },
  { type: 'FAQ', title: 'Approved FAQs', label: 'FAQ' },
  { type: 'MESSAGE', title: 'Messages', label: 'Message' },
  { type: 'EVENT', title: 'Events', label: 'Event' },
  { type: 'ANNOUNCEMENT', title: 'Announcements', label: 'Announcement' },
] as const

function hitDestination(
  serverId: string,
  hit: GlobalSearchHit,
  resourcesChannelId: string | null,
  variant: SearchDestinationVariant,
): string {
  if (hit.href?.startsWith('/app/')) return hit.href
  if (!hit.courseId) return `/app/servers/${serverId}/home`
  if (variant === 'v2') {
    return v2CoursePath(
      serverId,
      hit.courseId,
      hit.documentType === 'FAQ' ? 'questions' : 'resources',
    )
  }

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
  variant,
}: {
  onClose: () => void
  panelRef: RefObject<HTMLElement | null>
  variant: SearchDestinationVariant
}) {
  const { serverId: routeServerId, courseId: routeCourseId } = useParams()
  const rememberedServerId = readActiveStudyServerId() ?? undefined
  const requestedServerId = routeServerId ?? rememberedServerId
  const navigate = useNavigate()
  const navigationQuery = useStudyServerNavigationQuery(requestedServerId)
  const serverId =
    routeServerId ?? (navigationQuery.isError ? undefined : requestedServerId)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<GlobalSearchHit[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isSearching, setIsSearching] = useState(false)
  const [contentTypeFilter, setContentTypeFilter] = useState<ContentTypeFilter>('all')
  const [courseFilter, setCourseFilter] = useState<string>('all')

  const trimmedQuery = query.trim()

  const handleQueryChange = (value: string) => {
    setQuery(value)
    setResults([])
    setError(null)
    setIsSearching(value.trim().length >= 2)
    if (value.trim().length < 2) {
      setResults([])
      setError(null)
      setIsSearching(false)
    }
  }

  const courseIds = useMemo(
    () => new Set((navigationQuery.data?.courses ?? []).map((course) => course.id)),
    [navigationQuery.data?.courses],
  )
  const activeCourseFilter = variant === 'v2' && routeCourseId
    ? routeCourseId
    : courseFilter !== 'all' && !courseIds.has(courseFilter) ? 'all' : courseFilter

  useEffect(() => {
    if (!serverId || trimmedQuery.length < 2) {
      return
    }

    let cancelled = false
    let handle: number
    const refresh = () => {
      void searchStudyServer(serverId, trimmedQuery, {
        documentType: contentTypeFilter === 'all' ? undefined : contentTypeFilter,
        courseId: activeCourseFilter === 'all' ? undefined : activeCourseFilter,
      })
        .then((response) => {
          if (!cancelled) {
            setResults(response.results)
            setError(null)
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
            handle = window.setTimeout(refresh, 5000)
          }
        })
    }
    handle = window.setTimeout(refresh, 250)

    return () => {
      cancelled = true
      window.clearTimeout(handle)
    }
  }, [trimmedQuery, serverId, contentTypeFilter, activeCourseFilter])

  const groupedResults = useMemo(() => {
    const base = (serverId && trimmedQuery.length >= 2 ? results : []).filter((hit) => {
      if (hit.courseId && !courseIds.has(hit.courseId)) {
        return false
      }
      if (contentTypeFilter !== 'all' && hit.documentType !== contentTypeFilter) {
        return false
      }
      if (activeCourseFilter !== 'all' && hit.courseId !== activeCourseFilter) {
        return false
      }
      return true
    })

    return { all: base, sections: contentSections.map(section => ({ ...section, hits: base.filter(hit => hit.documentType === section.type) })) }
  }, [activeCourseFilter, contentTypeFilter, courseIds, results, serverId, trimmedQuery])

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
            <h2 className="text-sm font-semibold text-app-text">Study Server search</h2>
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
          Search resources, FAQs, messages and community
          <input
            autoFocus
            value={query}
            onChange={(event) => handleQueryChange(event.target.value)}
            placeholder="Search resources, FAQs, messages and community"
            className="rounded-lg border border-app-border bg-app-bg px-3 py-2 text-sm text-app-text"
          />
        </label>
        <div className="mt-3 flex flex-wrap gap-2">
          <FilterSelect
            label="Course"
            value={activeCourseFilter}
            onChange={value => { setCourseFilter(value); setResults([]); setIsSearching(trimmedQuery.length >= 2) }}
            disabled={variant === 'v2' && Boolean(routeCourseId)}
            options={[
              { value: 'all', label: 'Entire Study Server' },
              ...(navigationQuery.data?.courses.map((course) => ({
                value: course.id,
                label: course.title,
              })) ?? []),
            ]}
          />
          <FilterSelect
            label="Content"
            value={contentTypeFilter}
            onChange={value => { setContentTypeFilter(value as ContentTypeFilter); setResults([]); setIsSearching(trimmedQuery.length >= 2) }}
            options={[
              { value: 'all', label: 'All types' },
              { value: 'RESOURCE', label: 'Resources' },
              { value: 'FAQ', label: 'FAQs' },
              { value: 'MESSAGE', label: 'Messages' },
              { value: 'EVENT', label: 'Events' },
              { value: 'ANNOUNCEMENT', label: 'Announcements' },
            ]}
          />
        </div>
      </header>

      <div className="max-h-[min(28rem,calc(100dvh-18rem))] overflow-y-auto p-2">
        {!serverId ? (
          <p className="px-3 py-6 text-sm text-app-muted">
            Open a Study Server to search its content.
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


        {!isSearching && serverId && groupedResults.all.length === 0 && trimmedQuery.length >= 2 && !error ? (
          <p className="px-3 py-6 text-sm text-app-muted">No matches. Try another phrase or change your filters.</p>
        ) : null}

        {groupedResults.sections.map(section => <SearchResultSection
          key={section.type}
          title={section.title}
          count={section.hits.length}
          hits={section.hits}
          serverId={serverId}
          courseLookup={courseLookup}
          variant={variant}
          onNavigate={(destination) => {
            onClose()
            navigate(destination)
          }}
        />)}
      </div>


      {serverId ? (
        <footer className="border-t border-app-border px-4 py-3 text-xs text-app-muted">
          Results update automatically. Only content you can access appears.
        </footer>
      ) : null}
    </section>
  )
}

function FilterSelect({
  label,
  value,
  onChange,
  disabled = false,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  options: { value: string; label: string }[]
}) {
  return (
    <label className="text-[11px] text-app-muted">
      {label}
      <select
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        className="ml-1 rounded-md border border-app-border bg-app-bg px-2 py-1 text-xs text-app-text disabled:cursor-not-allowed disabled:opacity-60"
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
  variant,
  onNavigate,
}: {
  title: string
  count: number
  hits: GlobalSearchHit[]
  serverId: string | undefined
  courseLookup: {
    resourcesChannelByCourseId: Map<string, string>
  }
  variant: SearchDestinationVariant
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
          const resourcesChannelId = courseLookup.resourcesChannelByCourseId.get(hit.courseId ?? '') ?? null
          const destination = hitDestination(serverId ?? '', hit, resourcesChannelId, variant)

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
                  {contentSections.find(section => section.type === hit.documentType)?.label} · {hit.courseTitle}
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

export function GlobalSearchOverlay({ variant = 'legacy' }: { variant?: SearchDestinationVariant }) {
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
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/60 px-4 py-6 sm:py-16">
      <button
        type="button"
        aria-label="Close search"
        className="absolute inset-0"
        onClick={closeSearch}
      />
      <GlobalSearchOverlayPanel onClose={closeSearch} panelRef={panelRef} variant={variant} />
    </div>
  )
}
