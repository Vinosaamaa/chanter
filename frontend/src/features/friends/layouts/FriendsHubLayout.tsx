import { Outlet } from 'react-router-dom'

import { GlobalSearchOverlay } from '../../global-search/components/GlobalSearchOverlay'
import { GlobalSearchProvider } from '../../global-search/context/GlobalSearchProvider'
import { AppTopBar } from '../../shell/components/AppTopBar'

export function FriendsHubLayout() {
  return (
    <GlobalSearchProvider>
      <div className="flex h-screen flex-col overflow-hidden bg-app-bg text-app-text">
        <AppTopBar />
        <Outlet />
        <GlobalSearchOverlay />
      </div>
    </GlobalSearchProvider>
  )
}
