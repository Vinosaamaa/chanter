import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import { useAuthStore } from '../stores/auth-store'
import type { AuthSession } from '../features/auth/types'
import { AuthenticatedQueryCacheBoundary } from './AuthenticatedQueryCacheBoundary'

describe('AuthenticatedQueryCacheBoundary', () => {
  afterEach(() => {
    cleanup()
    useAuthStore.getState().clearSession()
  })

  it('keeps cached data for token refreshes and clears it when the user changes', async () => {
    const queryClient = new QueryClient()
    act(() => useAuthStore.getState().setSession(sessionFor('user-a', 'token-a')))

    render(
      <QueryClientProvider client={queryClient}>
        <AuthenticatedQueryCacheBoundary>
          <p>Authenticated app</p>
        </AuthenticatedQueryCacheBoundary>
      </QueryClientProvider>,
    )

    queryClient.setQueryData(['study-servers', 'user-a'], [{ id: 'private-server-a' }])
    act(() => useAuthStore.getState().setSession(sessionFor('user-a', 'refreshed-token')))
    expect(queryClient.getQueryData(['study-servers', 'user-a'])).toEqual([
      { id: 'private-server-a' },
    ])

    act(() => useAuthStore.getState().setSession(sessionFor('user-b', 'token-b')))
    await waitFor(() => {
      expect(queryClient.getQueryData(['study-servers', 'user-a'])).toBeUndefined()
    })
  })

  it('clears authenticated query data on sign-out', async () => {
    const queryClient = new QueryClient()
    act(() => useAuthStore.getState().setSession(sessionFor('user-a', 'token-a')))

    render(
      <QueryClientProvider client={queryClient}>
        <AuthenticatedQueryCacheBoundary>
          <p>Authenticated app</p>
        </AuthenticatedQueryCacheBoundary>
      </QueryClientProvider>,
    )

    queryClient.setQueryData(['friend-public-profiles', 'user-a'], [{ userId: 'peer-a' }])
    act(() => useAuthStore.getState().clearSession())

    await waitFor(() => {
      expect(queryClient.getQueryData(['friend-public-profiles', 'user-a'])).toBeUndefined()
    })
  })

  it('lets newly mounted account queries finish after cookie restoration', async () => {
    useAuthStore.getState().clearSession()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={queryClient}><AuthenticatedQueryCacheBoundary>
      <AccountQuery />
    </AuthenticatedQueryCacheBoundary></QueryClientProvider>)
    act(() => useAuthStore.getState().setSession(sessionFor('restored-user', 'restored-token')))
    expect(await screen.findByText('Courses for restored-user')).toBeInTheDocument()
  })
})

function AccountQuery() {
  const userId = useAuthStore((state) => state.user?.id)
  const query = useQuery({ queryKey: ['account-courses', userId], enabled: Boolean(userId),
    queryFn: async () => `Courses for ${userId}` })
  return <p>{query.data ?? 'Loading courses'}</p>
}

function sessionFor(userId: string, accessToken: string): AuthSession {
  return {
    accessToken,
    expiresInSeconds: 900,
    user: {
      id: userId,
      email: `${userId}@chanter.local`,
      displayName: userId,
    },
  }
}
