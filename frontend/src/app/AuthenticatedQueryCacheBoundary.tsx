import { useQueryClient } from '@tanstack/react-query'
import { useLayoutEffect, type ReactNode } from 'react'

import { useAuthStore } from '../stores/auth-store'

export function AuthenticatedQueryCacheBoundary({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  useLayoutEffect(() => useAuthStore.subscribe((state, previous) => {
    if (state.user?.id !== previous.user?.id) {
      // Clear before React mounts the next account's queries. Clearing in a
      // post-render effect also removes those new queries and leaves them pending.
      queryClient.clear()
    }
  }), [queryClient])

  return children
}
