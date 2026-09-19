import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { CommunityLoungePage } from './CommunityPages'

const state = vi.hoisted(() => ({ messages: [] as { id: string; senderUserId: string; body: string; createdAt: string }[] }))
vi.mock('../../layouts/v2-community-context', () => ({ useV2Community: () => ({ navigation: { studyServerChannels: [{ id: 'lounge', name: 'lounge', kind: 'TEXT' }] } }) }))
vi.mock('../../../shell/hooks/use-channel-conversation', () => ({ useChannelConversation: () => ({ messages: state.messages, connectionStatus: 'connected', isLoadingHistory: false, error: null, sendMessage: vi.fn(), isSending: false }) }))
vi.mock('../../../shell/channel-messages-api', () => ({ fetchChannelMessageAccess: async () => ({ canReadMessages: true, canPostMessages: true }) }))
vi.mock('../../../friends/friends-api', () => ({ fetchPublicProfiles: async () => ({ profiles: [{ userId: 'real-author', displayName: 'Course member' }] }) }))

afterEach(cleanup)
beforeEach(() => { state.messages = [] })
const mount = () => render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><CommunityLoungePage /></QueryClientProvider></MemoryRouter>)

it('shows an honest empty conversation without sample people or unsupported controls', async () => {
  mount()
  expect(await screen.findByText('No messages yet. Start the conversation.')).toBeVisible()
  expect(screen.queryByText('Priya Patel')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled()
  expect(screen.queryByRole('button', { name: 'Community Lounge' })).not.toBeInTheDocument()
})

it('resolves the sender profile and uses the message timestamp', async () => {
  state.messages = [{ id: 'message', senderUserId: 'real-author', body: 'The actual conversation', createdAt: '2026-08-03T16:05:00Z' }]
  mount()
  expect(await screen.findByText('Course member')).toBeVisible()
  expect(screen.getByText('The actual conversation')).toBeVisible()
  expect(document.querySelector('time')).toHaveAttribute('datetime', '2026-08-03T16:05:00Z')
})
