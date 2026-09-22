import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { FriendsPage } from './FriendsPage'

const mocks = vi.hoisted(() => ({
  preferredFriendId: null as string | null,
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
  useFriendsHub: (preferredFriendId?: string | null) => {
    mocks.preferredFriendId = preferredFriendId ?? null
    return mocks.hub
  },
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
    mocks.preferredFriendId = null
    mocks.hub.selectedFriendId = 'friend-alex'
    mocks.hub.callState.phase = 'idle'
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() { this.setAttribute('open', '') } })
    Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value() { this.removeAttribute('open') } })
  })

  it('renders real friend profiles, exact DM context, and no demo fallback', () => {
    renderPage()

    expect(screen.getAllByText('Alex Chen').length).toBeGreaterThan(0)
    expect(screen.getByText('Real message from Alex')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Message Alex Chen…')).toBeInTheDocument()
    expect(screen.queryByText('Taylor Johnson')).not.toBeInTheDocument()
    expect(screen.queryByText('Want to study calculus tonight?')).not.toBeInTheDocument()

    expect(screen.queryByRole('button', { name: /Attach file|Add emoji|Start video call/ })).not.toBeInTheDocument()
  })

  it('opens a friend conversation with a return path to the mobile friend list', async () => {
    const user = userEvent.setup()
    const view = renderPage()
    await user.click(within(screen.getByRole('complementary')).getByRole('button', { name: /Alex Chen/i }))
    expect(mocks.preferredFriendId).toBe('friend-alex')
    expect(view.container.querySelector('.friends-page')).toHaveClass('conversation-open')
    expect(screen.getByRole('heading', { name: 'Alex Chen' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'Back to friends' }))
    expect(view.container.querySelector('.friends-page')).not.toHaveClass('conversation-open')
    expect(within(screen.getByRole('complementary')).getByRole('button', { name: /Alex Chen/i })).toHaveFocus()
  })

  it('keeps the friend list available when a deep link has no active friend', () => {
    mocks.hub.selectedFriendId = 'missing-friend'
    const view = renderPage('/app/friends?friend=missing-friend')
    expect(view.container.querySelector('.friends-page')).not.toHaveClass('conversation-open')
  })

  it('removes the friend deep link when returning to the mobile list', async () => {
    const user = userEvent.setup()
    renderPage('/app/friends?friend=friend-alex')
    await user.click(screen.getByRole('button', { name: 'Back to friends' }))
    expect(mocks.preferredFriendId).toBeNull()
  })

  it('lists only co-member candidates and sends to the selected user id', async () => {
    const user = userEvent.setup()
    renderPage()

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
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Add friend' }))

    expect(screen.getByRole('textbox', { name: 'Search co-members' })).toHaveFocus()
    fireEvent(screen.getByRole('dialog', { name: 'Add a friend' }), new Event('cancel', { cancelable: true }))
    expect(screen.queryByRole('dialog', { name: 'Add a friend' })).not.toBeInTheDocument()
  })

  it('shows truthful incoming and outgoing actions with the real unread count', async () => {
    const user = userEvent.setup()
    renderPage()

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
    renderPage()

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

  it('passes a requested friend deep link into the accepted-friends hub', () => {
    renderPage('/app/friends?friend=friend-alex')

    expect(mocks.preferredFriendId).toBe('friend-alex')
  })

  it('moves focus into an incoming call and restores the prior control when the call ends', () => {
    const view = renderPage()
    const opener = screen.getByRole('button', { name: 'Start voice call with Alex Chen' })
    opener.focus()
    mocks.hub.callState.phase = 'incoming_ringing'
    view.rerender(<MemoryRouter><FriendsPage /></MemoryRouter>)
    expect(screen.getByRole('button', { name: 'Accept voice call' })).toHaveFocus()
    mocks.hub.callState.phase = 'idle'
    view.rerender(<MemoryRouter><FriendsPage /></MemoryRouter>)
    expect(opener).toHaveFocus()
  })
})

function renderPage(initialEntry = '/app/friends') {
  return render(<MemoryRouter initialEntries={[initialEntry]}><FriendsPage /></MemoryRouter>)
}
