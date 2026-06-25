import { create } from 'zustand'

type AppStore = {
  /** Placeholder until #49 wires real auth sessions. */
  sessionUserId: string | null
  setSessionUserId: (userId: string | null) => void
}

export const useAppStore = create<AppStore>((set) => ({
  sessionUserId: null,
  setSessionUserId: (userId) => set({ sessionUserId: userId }),
}))
