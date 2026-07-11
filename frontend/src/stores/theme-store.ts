import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type AppTheme = 'dark' | 'light'

type ThemeStore = {
  theme: AppTheme
  setTheme: (theme: AppTheme) => void
  toggleTheme: () => void
}

export const useThemeStore = create<ThemeStore>()(
  persist(
    (set, get) => ({
      theme: 'light',
      setTheme: (theme) => set({ theme }),
      toggleTheme: () => set({ theme: get().theme === 'dark' ? 'light' : 'dark' }),
    }),
    { name: 'chanter-theme' },
  ),
)
