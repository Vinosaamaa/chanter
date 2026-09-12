import { useCallback, useRef, useState } from 'react'
import { NavLink, Outlet, useParams } from 'react-router-dom'
import { Home, Inbox, LibraryBig, UsersRound } from 'lucide-react'

import { GlobalSearchOverlay } from '../../global-search/components/GlobalSearchOverlay'
import { GlobalSearchProvider } from '../../global-search/context/GlobalSearchProvider'
import { V2Sidebar } from '../components/V2Sidebar'
import { V2TopBar } from '../components/V2TopBar'
import { useV2SidebarData } from '../hooks/use-v2-sidebar-data'

export function V2AppShellLayout() {
  const { serverId } = useParams()
  const sidebar = useV2SidebarData(serverId)
  const [menuOpen, setMenuOpen] = useState(false)
  const menuTrigger = useRef<HTMLElement | null>(null)
  const openMenu = () => {
    menuTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setMenuOpen(true)
  }
  const closeMenu = useCallback(() => {
    setMenuOpen(false)
    requestAnimationFrame(() => menuTrigger.current?.focus())
  }, [])

  return (
    <GlobalSearchProvider>
      <div className="v2-app-shell h-dvh w-full">
        <a className="skip-link" href="#main-content">Skip to content</a>
        <div className="app-shell">
          <button
            type="button"
            className={`sidebar-backdrop ${menuOpen ? 'show' : ''}`}
            aria-hidden="true"
            tabIndex={-1}
            onClick={closeMenu}
          />

          <V2Sidebar data={sidebar} menuOpen={menuOpen} onCloseMenu={closeMenu} />

          <section className="content-shell" inert={menuOpen}>
            <V2TopBar onOpenMenu={openMenu} />
            <main id="main-content" tabIndex={-1} className="flex min-h-0 flex-1 flex-col overflow-hidden">
              <Outlet />
            </main>
            <nav className="mobile-navigation" aria-label="Mobile navigation">
              <NavLink to="/app/home"><Home aria-hidden="true" /><span>Home</span></NavLink>
              <NavLink to="/app/inbox"><Inbox aria-hidden="true" /><span>Inbox</span></NavLink>
              <NavLink to="/app/friends"><UsersRound aria-hidden="true" /><span>Friends</span></NavLink>
              <button type="button" onClick={openMenu} aria-expanded={menuOpen} aria-controls="course-navigation"><LibraryBig aria-hidden="true" /><span>Browse</span></button>
            </nav>
          </section>
        </div>
        <GlobalSearchOverlay variant="v2" />
      </div>
    </GlobalSearchProvider>
  )
}
