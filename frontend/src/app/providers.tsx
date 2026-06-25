import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { RouterProvider } from 'react-router-dom'

import { refreshSession } from '../features/auth/auth-api'
import { configureApiAuth } from '../lib/api-client'
import { useAuthStore } from '../stores/auth-store'
import { createAppRouter } from './router'

type AppProvidersProps = {
  children?: ReactNode
}

function ApiAuthBootstrap({ children }: { children: ReactNode }) {
  const setSession = useAuthStore((state) => state.setSession)
  const clearSession = useAuthStore((state) => state.clearSession)

  useEffect(() => {
    configureApiAuth({
      getAccessToken: () => useAuthStore.getState().accessToken,
      refreshSession: async () => {
        const currentRefreshToken = useAuthStore.getState().refreshToken
        if (!currentRefreshToken) {
          return false
        }

        try {
          const session = await refreshSession(currentRefreshToken)
          setSession(session)
          return true
        } catch {
          clearSession()
          return false
        }
      },
    })
  }, [clearSession, setSession])

  return <>{children}</>
}

export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
          },
        },
      }),
  )

  const [router] = useState(() => createAppRouter())

  if (children !== undefined) {
    return (
      <QueryClientProvider client={queryClient}>
        <ApiAuthBootstrap>{children}</ApiAuthBootstrap>
      </QueryClientProvider>
    )
  }

  return (
    <QueryClientProvider client={queryClient}>
      <ApiAuthBootstrap>
        <RouterProvider router={router} />
      </ApiAuthBootstrap>
    </QueryClientProvider>
  )
}
