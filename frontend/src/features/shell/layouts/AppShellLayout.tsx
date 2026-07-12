import { Outlet, useParams } from 'react-router-dom'
import { useEffect } from 'react'

import { ResizableShellPanel } from '../../../components/shell/ResizableShellPanel'
import { rememberActiveStudyServerId } from '../../../lib/last-active-study-server'
import {
  CHANNEL_SIDEBAR_DEFAULT_WIDTH,
  CHANNEL_SIDEBAR_MAX_WIDTH,
  CHANNEL_SIDEBAR_MIN_WIDTH,
  CONTEXT_PANEL_DEFAULT_WIDTH,
  CONTEXT_PANEL_MAX_WIDTH,
  CONTEXT_PANEL_MIN_WIDTH,
  useShellLayoutStore,
} from '../../../stores/shell-layout-store'

import { GlobalSearchOverlay } from '../../global-search/components/GlobalSearchOverlay'
import { GlobalSearchProvider } from '../../global-search/context/GlobalSearchProvider'
import { AppTopBar } from '../components/AppTopBar'
import { ChannelSidebarColumn } from '../components/ChannelSidebarColumn'
import { ShellContextPanel } from '../components/QuestionsContextPanel'
import { ChannelConversation } from '../components/ChannelConversation'
import { ServerSwitcherColumn } from '../components/ServerSwitcherColumn'
import { QuestionsPanelProvider } from '../context/questions-panel-context'

export function AppShellLayout() {
  const { serverId } = useParams()
  const channelSidebarWidth = useShellLayoutStore((state) => state.channelSidebarWidth)
  const channelSidebarCollapsed = useShellLayoutStore((state) => state.channelSidebarCollapsed)
  const contextPanelWidth = useShellLayoutStore((state) => state.contextPanelWidth)
  const contextPanelCollapsed = useShellLayoutStore((state) => state.contextPanelCollapsed)
  const setChannelSidebarWidth = useShellLayoutStore((state) => state.setChannelSidebarWidth)
  const setContextPanelWidth = useShellLayoutStore((state) => state.setContextPanelWidth)
  const toggleChannelSidebar = useShellLayoutStore((state) => state.toggleChannelSidebar)
  const toggleContextPanel = useShellLayoutStore((state) => state.toggleContextPanel)

  useEffect(() => {
    rememberActiveStudyServerId(serverId)
  }, [serverId])

  const showServerPanels = Boolean(serverId)

  return (
    <GlobalSearchProvider>
      <QuestionsPanelProvider>
        <div className="flex h-screen flex-col overflow-hidden bg-app-bg text-app-text">
          <AppTopBar />
          <div className="flex min-h-0 flex-1">
            <ServerSwitcherColumn />
            {showServerPanels ? (
              <ResizableShellPanel
                side="left"
                width={channelSidebarWidth || CHANNEL_SIDEBAR_DEFAULT_WIDTH}
                collapsed={channelSidebarCollapsed}
                minWidth={CHANNEL_SIDEBAR_MIN_WIDTH}
                maxWidth={CHANNEL_SIDEBAR_MAX_WIDTH}
                onWidthChange={setChannelSidebarWidth}
                onToggleCollapsed={toggleChannelSidebar}
                collapseLabel="Hide channel list"
                expandLabel="Show channel list"
              >
                <ChannelSidebarColumn />
              </ResizableShellPanel>
            ) : null}
            <Outlet />
            {showServerPanels ? (
              <ResizableShellPanel
                side="right"
                width={contextPanelWidth || CONTEXT_PANEL_DEFAULT_WIDTH}
                collapsed={contextPanelCollapsed}
                minWidth={CONTEXT_PANEL_MIN_WIDTH}
                maxWidth={CONTEXT_PANEL_MAX_WIDTH}
                onWidthChange={setContextPanelWidth}
                onToggleCollapsed={toggleContextPanel}
                collapseLabel="Hide context panel"
                expandLabel="Show context panel"
                className="border-l border-app-border bg-app-surface"
              >
                <ShellContextPanel />
              </ResizableShellPanel>
            ) : null}
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
