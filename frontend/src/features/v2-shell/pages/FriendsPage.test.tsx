import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { FriendsPage } from './FriendsPage'

const mocks = vi.hoisted(() => ({
  hub: {
    friends: [{ friendUserId: 'friend-alex', friendsSince: '2026-07-01T00:00:00Z' }],
    selectedFriendId: 'friend-alex',
    selectFriend: vi.fn(),
    refreshFriends: vi.fn().mockResolvedValue(undefined),
    messages: [
      {
        id: 'dm-1',
        senderUserId: 'friend-alex',
        recipientUserId: 'me',
        body: 'Real message from Alex',
        sentAt: '2026-07-13T18:30:00Z',
      },
    ],
    presenceByFriendId: { 'friend-alex': 'online' },
    connectionStatus: 'connected',
    isLoadingFriends: false,
    isLoadingMessages: false,
    friendsListError: null,
    error: null,
    sendMessage: vi.fn().mockResolvedValue(true),
    isSending: false,
    callState: { phase: 'idle', callId: null, peerUserId: null, reason: null },
    callError: null,
    isMuted: false,
    startCall: vi.fn(),
    acceptCall: vi.fn(),
    declineCall: vi.fn(),
    hangUpCall: vi.fn(),
    toggleCallMute: vi.fn(),
  },
  relationships: {
    profilesById: {
      'friend-alex': { userId: 'friend-alex', displayName: 'Alex Chen' },
      'candidate-priya': { userId: 'candidate-priya', displayName: 'Priya Sharma' },
      'request-morgan': { userId: 'request-morgan', displayName: 'Morgan Liu' },
      'request-noah': { userId: 'request-noah', displayName: 'Noah Williams' },
    },
    directoryEntries: [
      {
        userId: 'candidate-priya',
        displayName: 'Priya Sharma',
        sharedStudyServerName: 'Spring Bootcamp Hub',
        state: 'available',
      },
    ],
    incoming: [
      {
        id: 'incoming-1',
        senderUserId: 'request-morgan',
        recipientUserId: 'me',
        status: 'PENDING',
        createdAt: '2026-07-13T18:00:00Z',
      },
    ],
    outgoing: [
      {
        id: 'outgoing-1',
        senderUserId: 'me',
        recipientUserId: 'request-noah',
        status: 'PENDING',
        createdAt: '2026-07-13T18:10:00Z',
      },
    ],
    isLoading: false,
    error: null,
    actionError: null,
    busyActionIds: new Set<string>(),
    sendRequest: vi.fn(),
    acceptRequest: vi.fn(),
    declineRequest: vi.fn(),
    cancelRequest: vi.fn(),
    blockPeer: vi.fn(),
  },
}))

vi.mock('../../../stores/auth-store', () => ({
  useAuthStore: (selector: (state: { user: { id: string } }) => unknown) =>
    selector({ user: { id: 'me' } }),
}))

vi.mock('../../friends/hooks/use-friends-hub', () => ({
  useFriendsHub: () => mocks.hub,
}))

vi.mock('../../friends/hooks/use-friend-relationships', () => ({
  useFriendRelationships: () => mocks.relationships,
}))

describe('FriendsPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders real friend profiles, exact DM context, and no demo fallback', () => {
    render(<FriendsPage />)

    expect(screen.getAllByText('Alex Chen').length).toBeGreaterThan(0)
    expect(screen.getByText('Real message from Alex')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Message Alex Chen…')).toBeInTheDocument()
    expect(screen.queryByText('Taylor Johnson')).not.toBeInTheDocument()
    expect(screen.queryByText('Want to study calculus tonight?')).not.toBeInTheDocument()

    expect(screen.getByRole('button', { name: 'Attach file (not available yet)' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Add emoji (not available yet)' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Start video call (not available yet)' })).toBeDisabled()
  })

  it('lists only co-member candidates and sends to the selected user id', async () => {
    const user = userEvent.setup()
    render(<FriendsPage />)

    await user.click(screen.getByRole('button', { name: 'Add friend' }))

    expect(screen.getByRole('dialog', { name: 'Add a friend' })).toBeInTheDocument()
    expect(screen.getByText('Priya Sharma')).toBeInTheDocument()
    expect(screen.getByText('Spring Bootcamp Hub')).toBeInTheDocument()

    await user.click(
      screen.getByRole('button', { name: 'Send friend request to Priya Sharma' }),
    )
    expect(mocks.relationships.sendRequest).toHaveBeenCalledWith('candidate-priya')
  })

  it('opens the add-friend dialog on its search and closes it with Escape', async () => {
    const user = userEvent.setup()
    render(<FriendsPage />)

    await user.click(screen.getByRole('button', { name: 'Add friend' }))

    expect(screen.getByRole('textbox', { name: 'Search co-members' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Add a friend' })).not.toBeInTheDocument()
  })

  it('shows truthful incoming and outgoing actions with the real unread count', async () => {
    const user = userEvent.setup()
    render(<FriendsPage />)

    await user.click(screen.getByRole('button', { name: /Pending requests 1/i }))

    expect(screen.getByText('Morgan Liu')).toBeInTheDocument()
    expect(screen.getByText('Noah Williams')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Accept Morgan Liu' }))
    await user.click(screen.getByRole('button', { name: 'Cancel request to Noah Williams' }))

    expect(mocks.relationships.acceptRequest).toHaveBeenCalledWith('incoming-1')
    expect(mocks.relationships.cancelRequest).toHaveBeenCalledWith('outgoing-1')
  })

  it('requires confirmation before blocking the exact incoming peer', async () => {
    const user = userEvent.setup()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    render(<FriendsPage />)

    await user.click(screen.getByRole('button', { name: /Pending requests 1/i }))
    const blockButton = screen.getByRole('button', { name: 'Block Morgan Liu' })

    await user.click(blockButton)
    expect(confirm).toHaveBeenCalledWith(
      'Block Morgan Liu? They will be hidden from your friend requests and friends list.',
    )
    expect(mocks.relationships.blockPeer).not.toHaveBeenCalled()

    await user.click(blockButton)
    expect(mocks.relationships.blockPeer).toHaveBeenCalledWith('request-morgan')
  })
})
