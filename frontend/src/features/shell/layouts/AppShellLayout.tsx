import { Outlet } from 'react-router-dom'

import { GlobalSearchOverlay } from '../../global-search/components/GlobalSearchOverlay'
import { GlobalSearchProvider } from '../../global-search/context/GlobalSearchProvider'
import { AppTopBar } from '../components/AppTopBar'
import { ChannelSidebarColumn } from '../components/ChannelSidebarColumn'
import { ShellContextPanel } from '../components/QuestionsContextPanel'
import { ChannelConversation } from '../components/ChannelConversation'
import { ServerSwitcherColumn } from '../components/ServerSwitcherColumn'
import { QuestionsPanelProvider } from '../context/questions-panel-context'

export function AppShellLayout() {
  return (
    <GlobalSearchProvider>
      <QuestionsPanelProvider>
        <div className="flex h-screen flex-col overflow-hidden bg-app-bg text-app-text">
          <AppTopBar />
          <div className="flex min-h-0 flex-1">
            <ServerSwitcherColumn />
            <ChannelSidebarColumn />
            <Outlet />
            <ShellContextPanel />
          </div>
          <GlobalSearchOverlay />
        </div>
      </QuestionsPanelProvider>
    </GlobalSearchProvider>
  )
}

export function AppChannelLayout() {
  return <ChannelConversation />
}
