import { useLayoutEffect } from 'react'

import { applyThemeToDocument, useThemeStore } from '../../stores/theme-store'

export function ThemeSync() {
  const theme = useThemeStore((state) => state.theme)

  useLayoutEffect(() => {
    applyThemeToDocument(theme)
  }, [theme])

  return null
}
