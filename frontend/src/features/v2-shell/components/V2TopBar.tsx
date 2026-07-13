import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { Bell, Home as HomeIcon, Menu, Search } from 'lucide-react'

import { resolveV2SearchConfig } from '../v2-search-config'
import { useV2Chrome } from '../hooks/use-v2-chrome'

type V2TopBarProps = {
  notificationCount?: number
  onOpenMenu: () => void
}

export function V2TopBar({ notificationCount = 2, onOpenMenu }: V2TopBarProps) {
  const { pathname, pageTitle, showHomeIcon, breadcrumbs } = useV2Chrome()
  const search = resolveV2SearchConfig(pathname)
  const searchInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
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
          aria-label="Search courses, messages, and files"
          placeholder={search.placeholder}
          type="search"
        />
        <span>⌘K</span>
      </label>

      <button type="button" className="bell-button" aria-label="Notifications">
        <Bell size={29} />
        {notificationCount > 0 ? <b>{notificationCount}</b> : null}
      </button>
    </header>
  )
}
