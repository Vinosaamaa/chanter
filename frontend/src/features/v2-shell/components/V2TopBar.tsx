import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Bell, Home as HomeIcon, Menu, Search } from 'lucide-react'
import { useEffect, useState } from 'react'

import { useUnreadNotificationCountQuery } from '../../inbox/hooks/use-inbox-queries'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { useGlobalSearch } from '../../global-search/hooks/use-global-search'
import { resolveV2PrimaryNav } from '../v2-routes'
import { v2CommunityPath } from '../v2-routes'
import { resolveV2SearchConfig } from '../v2-search-config'

type V2TopBarProps = {
  onOpenMenu: () => void
}

function resolveTopBarChrome(pathname: string) {
  const primary = resolveV2PrimaryNav(pathname)
  const courseMatch = pathname.match(/^\/app\/servers\/[^/]+\/courses\/[^/]+/)
  if (courseMatch) {
    return {
      pageTitle: 'CS 101',
      showHomeIcon: false,
      breadcrumbs: [
        { label: 'Spring Bootcamp Hub' },
        { label: 'CS 101' },
      ],
    }
  }
  const communityMatch = pathname.match(/^\/app\/servers\/[^/]+\/community\//)
  if (communityMatch) {
    return { pageTitle: 'Community', showHomeIcon: false, breadcrumbs: [{ label: 'Spring Bootcamp Hub' }, { label: 'Community' }] }
  }
  const pageTitle = primary ? primary[0].toUpperCase() + primary.slice(1) : 'Home'
  return {
    pageTitle,
    showHomeIcon: primary === 'home',
    breadcrumbs: [] as { label: string; href?: string }[],
  }
}

export function V2TopBar({ onOpenMenu }: V2TopBarProps) {
  const { pathname, search: locationSearch } = useLocation()
  const primary = resolveV2PrimaryNav(pathname)
  const { pageTitle, showHomeIcon, breadcrumbs } = resolveTopBarChrome(pathname)
  const courseRoute = resolveCourseRoute(pathname)
  const search = resolveV2SearchConfig(pathname)
  const { openSearch } = useGlobalSearch()
  const unreadQuery = useUnreadNotificationCountQuery()
  const unreadCount = unreadQuery.data?.unreadCount ?? 0
  const calendarSearch = primary === 'calendar'

  return (
    <header className="topbar">
      <div className="page-label">
        <button
          type="button"
          className="menu-button"
          aria-label="Open navigation"
          onClick={onOpenMenu}
        >
          <Menu size={25} />
        </button>
        {courseRoute ? (
          <CourseBreadcrumbTrail
            serverId={courseRoute.serverId}
            courseId={courseRoute.courseId}
            cohortId={new URLSearchParams(locationSearch).get('cohort')}
          />
        ) : breadcrumbs.length > 0 ? (
          <nav className="breadcrumb-trail" aria-label="Breadcrumb">
            {breadcrumbs.map((segment, index) => (
              <span key={`${segment.label}-${index}`}>
                {index > 0 ? <span className="breadcrumb-sep"> / </span> : null}
                {segment.href ? (
                  <Link to={segment.href}>{segment.label}</Link>
                ) : (
                  <span>{segment.label}</span>
                )}
              </span>
            ))}
          </nav>
        ) : (
          <>
            {showHomeIcon ? <HomeIcon size={27} /> : null}
            <span>{pageTitle}</span>
          </>
        )}
      </div>

      <label className="search-box">
        <Search size={28} />
        {calendarSearch ? (
          <CalendarSearchInput placeholder={search.placeholder} />
        ) : (
          <input
            aria-label={search.placeholder.replace('…', '')}
            placeholder={search.placeholder}
            type="search"
            readOnly
            onClick={openSearch}
          />
        )}
        <span>⌘F</span>
      </label>

      <Link
        to="/app/inbox"
        className="bell-button"
        aria-label={unreadCount > 0 ? `Open inbox, ${unreadCount} unread` : 'Open inbox'}
      >
        <Bell size={29} />
        {unreadCount > 0 ? <b>{unreadCount > 99 ? '99+' : unreadCount}</b> : null}
      </Link>
    </header>
  )
}

function resolveCourseRoute(pathname: string): { serverId: string; courseId: string } | null {
  const match = pathname.match(/^\/app\/servers\/([^/]+)\/courses\/([^/]+)/)
  return match ? { serverId: match[1], courseId: match[2] } : null
}

function CalendarSearchInput({ placeholder }: { placeholder: string }) {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const urlQuery = searchParams.get('q') ?? ''
  const [draft, setDraft] = useState(urlQuery)

  useEffect(() => {
    setDraft(urlQuery)
  }, [urlQuery])

  useEffect(() => {
    const handle = window.setTimeout(() => {
      const trimmed = draft.trim()
      const params = new URLSearchParams(window.location.search)
      const current = params.get('q') ?? ''
      if (trimmed === current) return
      if (trimmed) {
        params.set('q', trimmed)
      } else {
        params.delete('q')
      }
      const query = params.toString()
      navigate({ pathname: '/app/calendar', search: query ? `?${query}` : '' }, { replace: true })
    }, 250)
    return () => window.clearTimeout(handle)
  }, [draft, navigate])

  return (
    <input
      aria-label={placeholder.replace('…', '')}
      placeholder={placeholder}
      type="search"
      value={draft}
      onChange={(event) => setDraft(event.target.value)}
    />
  )
}

function CourseBreadcrumbTrail({
  serverId,
  courseId,
  cohortId,
}: {
  serverId: string
  courseId: string
  cohortId: string | null
}) {
  const navigation = useStudyServerNavigationQuery(serverId)
  const course = navigation.data?.courses.find((candidate) => candidate.id === courseId)
  const selectedCohort = course?.cohorts.find((cohort) => cohort.id === cohortId)
  const segments = [
    { label: navigation.data?.studyServerName ?? 'Study Server', href: v2CommunityPath(serverId) },
    { label: course?.title ?? 'Course' },
    ...(selectedCohort && (course?.cohorts.length ?? 0) > 1
      ? [{ label: selectedCohort.name }]
      : []),
  ]

  return (
    <nav className="breadcrumb-trail" aria-label="Breadcrumb">
      {segments.map((segment, index) => (
        <span key={`${segment.label}-${index}`}>
          {index > 0 ? <span className="breadcrumb-sep"> / </span> : null}
          {segment.href ? <Link to={segment.href}>{segment.label}</Link> : <span>{segment.label}</span>}
        </span>
      ))}
    </nav>
  )
}
