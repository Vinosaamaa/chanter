import { useState } from 'react'
import { Outlet, useParams } from 'react-router-dom'

import { GlobalSearchOverlay } from '../../global-search/components/GlobalSearchOverlay'
import { GlobalSearchProvider } from '../../global-search/context/GlobalSearchProvider'
import { V2Sidebar } from '../components/V2Sidebar'
import { V2TopBar } from '../components/V2TopBar'
import { useV2SidebarData } from '../hooks/use-v2-sidebar-data'

export function V2AppShellLayout() {
  const { serverId } = useParams()
  const sidebar = useV2SidebarData(serverId)
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <GlobalSearchProvider>
      <div className="v2-app-shell h-dvh w-full">
        <div className="app-shell">
          <button
            type="button"
            className={`sidebar-backdrop ${menuOpen ? 'show' : ''}`}
            aria-label="Close navigation"
            onClick={() => setMenuOpen(false)}
          />

          <V2Sidebar data={sidebar} menuOpen={menuOpen} onCloseMenu={() => setMenuOpen(false)} />

          <section className="content-shell">
            <V2TopBar onOpenMenu={() => setMenuOpen(true)} />
            <main className="flex min-h-0 flex-1 flex-col overflow-hidden">
              <Outlet />
            </main>
          </section>
        </div>
        <GlobalSearchOverlay variant="v2" />
      </div>
    </GlobalSearchProvider>
  )
}
