import { Outlet } from 'react-router-dom'

import { AppTopBar } from '../components/AppTopBar'
import { ChannelSidebarColumn } from '../components/ChannelSidebarColumn'
import { ContextPlaceholder } from '../components/ContextPlaceholder'
import { ChannelConversation } from '../components/ChannelConversation'
import { ServerSwitcherColumn } from '../components/ServerSwitcherColumn'

export function AppShellLayout() {
  return (
    <div className="flex h-screen flex-col overflow-hidden bg-app-bg text-app-text">
      <AppTopBar />
      <div className="flex min-h-0 flex-1">
        <ServerSwitcherColumn />
        <ChannelSidebarColumn />
        <Outlet />
        <ContextPlaceholder />
      </div>
    </div>
  )
}

export function AppChannelLayout() {
  return <ChannelConversation />
}
