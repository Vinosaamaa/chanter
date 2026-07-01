import { createContext } from 'react'

export type GlobalSearchContextValue = {
  isOpen: boolean
  openSearch: () => void
  closeSearch: () => void
}

export const GlobalSearchContext = createContext<GlobalSearchContextValue | null>(null)
