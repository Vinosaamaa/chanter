import { Outlet } from 'react-router-dom'

import { AppTopBar } from '../components/AppTopBar'
import { ChannelSidebarColumn } from '../components/ChannelSidebarColumn'
import { ShellContextPanel } from '../components/QuestionsContextPanel'
import { ChannelConversation } from '../components/ChannelConversation'
import { ServerSwitcherColumn } from '../components/ServerSwitcherColumn'
import { QuestionsPanelProvider } from '../context/questions-panel-context'

export function AppShellLayout() {
  return (
    <QuestionsPanelProvider>
      <div className="flex h-screen flex-col overflow-hidden bg-app-bg text-app-text">
        <AppTopBar />
        <div className="flex min-h-0 flex-1">
          <ServerSwitcherColumn />
          <ChannelSidebarColumn />
          <Outlet />
          <ShellContextPanel />
        </div>
      </div>
    </QuestionsPanelProvider>
  )
}

export function AppChannelLayout() {
  return <ChannelConversation />
}
