import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { InboxPage } from './InboxPage'

const hooks = vi.hoisted(() => ({
  useNotificationsQuery: vi.fn(),
  useMarkNotificationReadMutation: vi.fn(),
  useMarkNotificationDoneMutation: vi.fn(),
}))

vi.mock('../../inbox/hooks/use-inbox-queries', () => hooks)

const sampleNotification = {
  id: 'n1',
  userId: 'user-1',
  kind: 'SUPPORT_QUESTION_ANSWERED',
  filterBucket: 'MENTIONS',
  title: 'Your question was answered',
  bodyPreview: 'Merge Sort is O(n log n).',
  courseLabel: 'CS 101',
  href: '/app/servers/s1/courses/c1/questions',
  sourceType: 'SUPPORT_QUESTION',
  sourceId: 'q1',
  studyServerId: 's1',
  courseId: 'c1',
  cohortId: null,
  channelId: 'ch1',
  createdAt: new Date().toISOString(),
  readAt: null,
  doneAt: null,
  unread: true,
}

function renderInbox() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <InboxPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('InboxPage', () => {
  const markReadMutate = vi.fn()
  const markDoneMutate = vi.fn()

  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 'user-1', email: 'sam@example.com', displayName: 'Sam Lee' },
    })
    hooks.useNotificationsQuery.mockReturnValue({
      data: { notifications: [sampleNotification] },
      isLoading: false,
      isError: false,
    })
    hooks.useMarkNotificationReadMutation.mockReturnValue({
      mutate: markReadMutate,
      isPending: false,
    })
    hooks.useMarkNotificationDoneMutation.mockReturnValue({
      mutate: markDoneMutate,
      isPending: false,
    })
  })

  it('loads notifications, hides reply composer, and marks done', async () => {
    const user = userEvent.setup()
    renderInbox()

    expect(await screen.findAllByText('Your question was answered')).not.toHaveLength(0)
    expect(screen.queryByLabelText('Reply to thread')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Open in course/i })).toHaveAttribute(
      'href',
      '/app/servers/s1/courses/c1/questions',
    )
    expect(markReadMutate).toHaveBeenCalledWith('n1')

    await user.click(screen.getByRole('button', { name: /Mark done/i }))
    expect(markDoneMutate).toHaveBeenCalledWith('n1', expect.any(Object))
  })

  it('filters Mentions via API', async () => {
    const user = userEvent.setup()
    renderInbox()
    await screen.findAllByText('Your question was answered')

    await user.click(screen.getByRole('button', { name: 'Mentions' }))
    await waitFor(() =>
      expect(hooks.useNotificationsQuery).toHaveBeenCalledWith('MENTIONS', 'OPEN'),
    )
  })
})
