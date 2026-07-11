import { useEffect } from 'react'

import { useThemeStore } from '../../stores/theme-store'

export function ThemeSync() {
  const theme = useThemeStore((state) => state.theme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.style.colorScheme = theme
  }, [theme])

  return null
}
