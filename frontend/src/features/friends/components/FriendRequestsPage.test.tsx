import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { FriendRequestsPage } from './FriendRequestsPage'

const incomingRequest = {
  id: 'req-1',
  senderUserId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  recipientUserId: 'viewer',
  status: 'PENDING' as const,
  createdAt: '2026-07-05T12:00:00Z',
}

vi.mock('../hooks/use-friend-requests-queries', () => ({
  friendRequestsQueryKey: ['friend-requests'],
  useFriendRequestsQuery: vi.fn(),
  usePendingFriendRequestCount: vi.fn(() => ({ incomingCount: 1 })),
}))

vi.mock('../friends-api', () => ({
  acceptFriendRequest: vi.fn(),
  blockUser: vi.fn(),
  cancelFriendRequest: vi.fn(),
  declineFriendRequest: vi.fn(),
}))

import { useFriendRequestsQuery } from '../hooks/use-friend-requests-queries'

const mockedUseFriendRequestsQuery = vi.mocked(useFriendRequestsQuery)

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <FriendRequestsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('FriendRequestsPage', () => {
  beforeEach(() => {
    mockedUseFriendRequestsQuery.mockReset()
  })

  it('renders incoming friend requests with actions', async () => {
    mockedUseFriendRequestsQuery.mockReturnValue({
      data: { incoming: [incomingRequest], outgoing: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useFriendRequestsQuery>)

    renderPage()

    expect(screen.getByRole('heading', { name: /pending requests/i })).toBeInTheDocument()
    expect(screen.getByText(/friend aaaaaaaa/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Accept' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Decline' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Block' })).toBeInTheDocument()
  })

  it('shows an empty state when there are no pending requests', () => {
    mockedUseFriendRequestsQuery.mockReturnValue({
      data: { incoming: [], outgoing: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useFriendRequestsQuery>)

    renderPage()

    expect(screen.getByText(/no incoming friend requests right now/i)).toBeInTheDocument()
  })
})
