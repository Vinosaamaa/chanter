import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type AppTheme = 'dark' | 'light'

function normalizeTheme(value: unknown): AppTheme {
  return value === 'dark' ? 'dark' : 'light'
}

type ThemeStore = {
  theme: AppTheme
  setTheme: (theme: AppTheme) => void
  toggleTheme: () => void
}

export const useThemeStore = create<ThemeStore>()(
  persist(
    (set, get) => ({
      theme: 'light',
      setTheme: (theme) => set({ theme: normalizeTheme(theme) }),
      toggleTheme: () => set({ theme: get().theme === 'dark' ? 'light' : 'dark' }),
    }),
    {
      name: 'chanter-theme',
      onRehydrateStorage: () => (state) => {
        if (!state) {
          return
        }
        const theme = normalizeTheme(state.theme)
        if (theme !== state.theme) {
          state.setTheme(theme)
        }
        applyThemeToDocument(theme)
      },
    },
  ),
)

export function applyThemeToDocument(theme: AppTheme) {
  if (typeof document === 'undefined') {
    return
  }
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
}
