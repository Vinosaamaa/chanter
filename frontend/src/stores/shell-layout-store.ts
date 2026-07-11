import { create } from 'zustand'
import { persist } from 'zustand/middleware'

type ShellLayoutStore = {
  channelSidebarWidth: number
  channelSidebarCollapsed: boolean
  contextPanelWidth: number
  contextPanelCollapsed: boolean
  collapsedSidebarSections: string[]
  setChannelSidebarWidth: (width: number) => void
  setContextPanelWidth: (width: number) => void
  toggleChannelSidebar: () => void
  toggleContextPanel: () => void
  toggleSidebarSection: (sectionKey: string) => void
  isSidebarSectionCollapsed: (sectionKey: string) => boolean
}

export const CHANNEL_SIDEBAR_MIN_WIDTH = 200
export const CHANNEL_SIDEBAR_MAX_WIDTH = 400
export const CHANNEL_SIDEBAR_DEFAULT_WIDTH = 240

export const CONTEXT_PANEL_MIN_WIDTH = 260
export const CONTEXT_PANEL_MAX_WIDTH = 520
export const CONTEXT_PANEL_DEFAULT_WIDTH = 320

export const useShellLayoutStore = create<ShellLayoutStore>()(
  persist(
    (set, get) => ({
      channelSidebarWidth: CHANNEL_SIDEBAR_DEFAULT_WIDTH,
      channelSidebarCollapsed: false,
      contextPanelWidth: CONTEXT_PANEL_DEFAULT_WIDTH,
      contextPanelCollapsed: false,
      collapsedSidebarSections: [],
      setChannelSidebarWidth: (width) => set({ channelSidebarWidth: width }),
      setContextPanelWidth: (width) => set({ contextPanelWidth: width }),
      toggleChannelSidebar: () =>
        set((state) => ({ channelSidebarCollapsed: !state.channelSidebarCollapsed })),
      toggleContextPanel: () =>
        set((state) => ({ contextPanelCollapsed: !state.contextPanelCollapsed })),
      toggleSidebarSection: (sectionKey) =>
        set((state) => {
          const collapsed = new Set(state.collapsedSidebarSections)
          if (collapsed.has(sectionKey)) {
            collapsed.delete(sectionKey)
          } else {
            collapsed.add(sectionKey)
          }
          return { collapsedSidebarSections: [...collapsed] }
        }),
      isSidebarSectionCollapsed: (sectionKey) =>
        get().collapsedSidebarSections.includes(sectionKey),
    }),
    { name: 'chanter-shell-layout' },
  ),
)
