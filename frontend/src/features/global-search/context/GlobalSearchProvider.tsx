import { useCallback, useMemo, useState, type ReactNode } from 'react'

import { useGlobalSearchShortcut } from '../hooks/use-global-search-shortcut'

import { GlobalSearchContext } from './global-search-context'

export function GlobalSearchProvider({ children }: { children: ReactNode }) {
  const [isOpen, setIsOpen] = useState(false)

  const openSearch = useCallback(() => setIsOpen(true), [])
  const closeSearch = useCallback(() => setIsOpen(false), [])

  useGlobalSearchShortcut({ isOpen, onOpen: openSearch, onClose: closeSearch })

  const value = useMemo(
    () => ({
      isOpen,
      openSearch,
      closeSearch,
    }),
    [closeSearch, isOpen, openSearch],
  )

  return <GlobalSearchContext.Provider value={value}>{children}</GlobalSearchContext.Provider>
}
