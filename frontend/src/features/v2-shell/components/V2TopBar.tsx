import { useEffect, useRef } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Bell, Home as HomeIcon, Menu, Search } from 'lucide-react'

import { resolveV2PrimaryNav } from '../v2-routes'
import { resolveV2SearchConfig } from '../v2-search-config'

type V2TopBarProps = {
  notificationCount?: number
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
  const pageTitle = primary ? primary[0].toUpperCase() + primary.slice(1) : 'Home'
  return {
    pageTitle,
    showHomeIcon: primary === 'home',
    breadcrumbs: [] as { label: string; href?: string }[],
  }
}

export function V2TopBar({ notificationCount = 2, onOpenMenu }: V2TopBarProps) {
  const { pathname } = useLocation()
  const { pageTitle, showHomeIcon, breadcrumbs } = resolveTopBarChrome(pathname)
  const search = resolveV2SearchConfig(pathname)
  const searchInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'f') {
        event.preventDefault()
        searchInputRef.current?.focus()
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

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
        {breadcrumbs.length > 0 ? (
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
        <input
          ref={searchInputRef}
          aria-label={search.placeholder.replace('…', '')}
          placeholder={search.placeholder}
          type="search"
        />
        <span>⌘F</span>
      </label>

      <button type="button" className="bell-button" aria-label="Notifications">
        <Bell size={29} />
        {notificationCount > 0 ? <b>{notificationCount}</b> : null}
      </button>
    </header>
  )
}
