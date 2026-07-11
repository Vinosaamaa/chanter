import { useLayoutEffect } from 'react'

import { useThemeStore } from '../../stores/theme-store'

function applyTheme(theme: 'dark' | 'light') {
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
}

export function ThemeSync() {
  const theme = useThemeStore((state) => state.theme)

  useLayoutEffect(() => {
    applyTheme(theme)
  }, [theme])

  return null
}
