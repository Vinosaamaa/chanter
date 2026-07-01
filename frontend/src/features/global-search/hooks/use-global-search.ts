import { useContext } from 'react'

import { GlobalSearchContext } from '../context/global-search-context'

export function useGlobalSearch() {
  const context = useContext(GlobalSearchContext)
  if (!context) {
    throw new Error('useGlobalSearch must be used within GlobalSearchProvider')
  }
  return context
}
