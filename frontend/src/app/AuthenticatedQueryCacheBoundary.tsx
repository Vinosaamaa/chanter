import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, type ReactNode } from 'react'

import { useAuthStore } from '../stores/auth-store'

export function AuthenticatedQueryCacheBoundary({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const previousUserId = useRef(userId)

  useEffect(() => {
    if (previousUserId.current === userId) return

    queryClient.clear()
    previousUserId.current = userId
  }, [queryClient, userId])

  return children
}
