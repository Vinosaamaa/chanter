import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { RouterProvider } from 'react-router-dom'

import { ThemeSync } from '../components/theme/ThemeSync'
import { AuthenticatedQueryCacheBoundary } from './AuthenticatedQueryCacheBoundary'
import './api-auth'
import { createAppRouter } from './router'

type AppProvidersProps = {
  children?: ReactNode
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
        <AuthenticatedQueryCacheBoundary>{children}</AuthenticatedQueryCacheBoundary>
      </QueryClientProvider>
    )
  }

  return (
    <QueryClientProvider client={queryClient}>
      <AuthenticatedQueryCacheBoundary>
        <ThemeSync />
        <RouterProvider router={router} />
      </AuthenticatedQueryCacheBoundary>
    </QueryClientProvider>
  )
}
