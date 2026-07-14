import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  Ban,
  Check,
  CheckCheck,
  Mic,
  MicOff,
  Paperclip,
  Phone,
  Plus,
  Send,
  Smile,
  UserPlus,
  Video,
  X,
} from 'lucide-react'

import { useAuthStore } from '../../../stores/auth-store'
import { useFriendRelationships } from '../../friends/hooks/use-friend-relationships'
import { useFriendsHub } from '../../friends/hooks/use-friends-hub'
import type { FriendRequest } from '../../friends/types'
import { V2Avatar } from '../components/V2Avatar'

type FriendsTab = 'friends' | 'pending'
type AvatarTone = 'blue' | 'purple' | 'amber' | 'green'

export function FriendsPage() {
  const hub = useFriendsHub()
  const user = useAuthStore((state) => state.user)
  const friendUserIds = useMemo(
    () => hub.friends.map((friend) => friend.friendUserId),
    [hub.friends],
  )
  const relationships = useFriendRelationships(friendUserIds, hub.refreshFriends)
  const [tab, setTab] = useState<FriendsTab>('friends')
  const [draft, setDraft] = useState('')
  const [showAdd, setShowAdd] = useState(false)
  const friendRows = useMemo(
    () =>
      hub.friends.map((friend) => ({
        id: friend.friendUserId,
        name: relationships.profilesById[friend.friendUserId]?.displayName ?? 'Account unavailable',
        tone: avatarTone(friend.friendUserId),
        online: hub.presenceByFriendId[friend.friendUserId] === 'online',
      })),
    [hub.friends, hub.presenceByFriendId, relationships.profilesById],
  )
  const active = friendRows.find((friend) => friend.id === hub.selectedFriendId) ?? null
  const callFriendName = hub.callState.peerUserId
    ? relationships.profilesById[hub.callState.peerUserId]?.displayName ?? 'Friend'
    : active?.name ?? 'Friend'

  const submitMessage = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const submitted = draft.trim()
    if (!submitted || !active) return

    void hub.sendMessage(submitted).then((sent) => {
      if (sent) setDraft((current) => (current.trim() === submitted ? '' : current))
    })
  }

  return (
    <div className="friends-page">
      <aside className="friends-list-pane">
        <header>
          <h1>Friends</h1>
          <button type="button" onClick={() => setShowAdd(true)}>
            Add friend <Plus />
          </button>
        </header>
        <nav aria-label="Friends views">
          <button
            type="button"
            className={tab === 'friends' ? 'active' : undefined}
            onClick={() => setTab('friends')}
          >
            Friends
          </button>
          <button
            type="button"
            className={tab === 'pending' ? 'active' : undefined}
            onClick={() => setTab('pending')}
          >
            Pending requests <b>{relationships.incoming.length}</b>
          </button>
        </nav>

        {tab === 'friends' ? (
          <FriendList
            friends={friendRows}
            selectedFriendId={hub.selectedFriendId}
            isLoading={hub.isLoadingFriends}
            error={hub.friendsListError}
            onSelect={hub.selectFriend}
          />
        ) : (
          <PendingRequests relationships={relationships} />
        )}
      </aside>

      <section className="dm-pane">
        {active ? (
          <>
            <header>
              <V2Avatar
                name={active.name}
                tone={active.tone}
                size="lg"
                online={active.online}
              />
              <div>
                <h2>{active.name}</h2>
                <p className={active.online ? undefined : 'offline'}>
                  {active.online ? 'Online' : 'Offline'}
                </p>
              </div>
              <button
                type="button"
                onClick={hub.startCall}
                disabled={hub.connectionStatus !== 'connected' || hub.callState.phase !== 'idle'}
                aria-label={`Start voice call with ${active.name}`}
                title={
                  hub.connectionStatus === 'connected'
                    ? 'Start voice call'
                    : 'Voice call is available when realtime reconnects'
                }
              >
                <Phone />
              </button>
              <button
                type="button"
                aria-label="Start video call (not available yet)"
                title="Video calls are not available yet"
                disabled
              >
                <Video />
              </button>
            </header>

            {hub.error ? (
              <p className="inline-error" role="alert">
                {hub.error}
              </p>
            ) : null}

            <div className="dm-message-list">
              <span className="today-divider">Conversation</span>
              {hub.isLoadingMessages ? (
                <p className="dm-empty">Loading message history…</p>
              ) : hub.messages.length === 0 ? (
                <p className="dm-empty">No messages yet. Say hello.</p>
              ) : (
                hub.messages.map((message) => {
                  const own = message.senderUserId === user?.id
                  return (
                    <article className={own ? 'own' : undefined} key={message.id}>
                      {!own ? (
                        <V2Avatar name={active.name} tone={active.tone} size="md" />
                      ) : null}
                      <div>
                        <p>{message.body}</p>
                        <time dateTime={message.sentAt}>
                          {formatTime(message.sentAt)}
                          {own ? <CheckCheck /> : null}
                        </time>
                      </div>
                    </article>
                  )
                })
              )}
            </div>

            <form onSubmit={submitMessage}>
              <button
                type="button"
                aria-label="Attach file (not available yet)"
                title="DM attachments are not available yet"
                disabled
              >
                <Paperclip />
              </button>
              <input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                placeholder={`Message ${active.name}…`}
                aria-label={`Message ${active.name}`}
                maxLength={4000}
              />
              <button
                type="button"
                aria-label="Add emoji (not available yet)"
                title="Emoji picker is not available yet"
                disabled
              >
                <Smile />
              </button>
              <button
                type="submit"
                className="send"
                disabled={!draft.trim() || hub.isSending}
                aria-label="Send message"
              >
                <Send />
              </button>
            </form>
          </>
        ) : (
          <div className="dm-empty-state">
            <UserPlus />
            <h2>Select a friend</h2>
            <p>Accepted friends appear here with their direct message history.</p>
          </div>
        )}
      </section>

      {showAdd ? (
        <AddFriendModal relationships={relationships} onClose={() => setShowAdd(false)} />
      ) : null}
      {hub.callState.phase !== 'idle' ? (
        <CallModal hub={hub} friendName={callFriendName} />
      ) : null}
    </div>
  )
}

type FriendRow = {
  id: string
  name: string
  tone: AvatarTone
  online: boolean
}

function FriendList({
  friends,
  selectedFriendId,
  isLoading,
  error,
  onSelect,
}: {
  friends: FriendRow[]
  selectedFriendId: string | null
  isLoading: boolean
  error: string | null
  onSelect: (friendUserId: string) => void
}) {
  if (isLoading) return <p className="friends-state">Loading friends…</p>
  if (error) {
    return (
      <p className="friends-state error" role="alert">
        {error}
      </p>
    )
  }
  if (friends.length === 0) {
    return <p className="friends-state">No accepted friends yet. Add a co-member to begin.</p>
  }

  const online = friends.filter((friend) => friend.online)
  const offline = friends.filter((friend) => !friend.online)
  return (
    <div className="friends-list">
      <h2>
        ONLINE <b>{online.length}</b>
      </h2>
      {online.map((friend) => (
        <FriendButton
          key={friend.id}
          friend={friend}
          active={selectedFriendId === friend.id}
          onClick={() => onSelect(friend.id)}
        />
      ))}
      <h2>
        ALL <b>{friends.length}</b>
      </h2>
      {offline.map((friend) => (
        <FriendButton
          key={friend.id}
          friend={friend}
          active={selectedFriendId === friend.id}
          onClick={() => onSelect(friend.id)}
        />
      ))}
    </div>
  )
}

function FriendButton({
  friend,
  active,
  onClick,
}: {
  friend: FriendRow
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      className={active ? 'active' : undefined}
      onClick={onClick}
      aria-current={active ? 'true' : undefined}
    >
      <V2Avatar name={friend.name} tone={friend.tone} size="md" online={friend.online} />
      <span>{friend.name}</span>
    </button>
  )
}

type FriendRelationships = ReturnType<typeof useFriendRelationships>

function PendingRequests({ relationships }: { relationships: FriendRelationships }) {
  if (relationships.isLoading) {
    return <p className="friends-state">Loading friend requests…</p>
  }
  if (relationships.error) {
    return (
      <p className="friends-state error" role="alert">
        {relationships.error}
      </p>
    )
  }

  return (
    <div className="pending-friends">
      {relationships.actionError ? (
        <p className="inline-error" role="alert">
          {relationships.actionError}
        </p>
      ) : null}
      <h2>INCOMING · {relationships.incoming.length}</h2>
      {relationships.incoming.map((request) => (
        <PendingRequestRow
          key={request.id}
          request={request}
          direction="incoming"
          relationships={relationships}
        />
      ))}
      {relationships.incoming.length === 0 ? <p>No incoming requests.</p> : null}

      <h2 className="outgoing-heading">OUTGOING · {relationships.outgoing.length}</h2>
      {relationships.outgoing.map((request) => (
        <PendingRequestRow
          key={request.id}
          request={request}
          direction="outgoing"
          relationships={relationships}
        />
      ))}
      {relationships.outgoing.length === 0 ? <p>No outgoing requests.</p> : null}
    </div>
  )
}

function PendingRequestRow({
  request,
  direction,
  relationships,
}: {
  request: FriendRequest
  direction: 'incoming' | 'outgoing'
  relationships: FriendRelationships
}) {
  const peerUserId = direction === 'incoming' ? request.senderUserId : request.recipientUserId
  const peerName = relationships.profilesById[peerUserId]?.displayName ?? 'Account unavailable'
  const isBusy =
    relationships.busyActionIds.has(request.id) || relationships.busyActionIds.has(peerUserId)

  return (
    <article>
      <V2Avatar name={peerName} tone={avatarTone(peerUserId)} size="md" />
      <span>
        <strong>{peerName}</strong>
        <small>{direction === 'incoming' ? 'Wants to connect' : 'Request sent'}</small>
      </span>
      <div className="pending-actions">
        {direction === 'incoming' ? (
          <>
            <button
              type="button"
              onClick={() => void relationships.acceptRequest(request.id)}
              disabled={isBusy}
              aria-label={`Accept ${peerName}`}
              title="Accept"
            >
              <Check />
            </button>
            <button
              type="button"
              onClick={() => void relationships.declineRequest(request.id)}
              disabled={isBusy}
              aria-label={`Decline ${peerName}`}
              title="Decline"
            >
              <X />
            </button>
            <button
              type="button"
              onClick={() => {
                if (
                  !window.confirm(
                    `Block ${peerName}? They will be hidden from your friend requests and friends list.`,
                  )
                ) {
                  return
                }
                void relationships.blockPeer(peerUserId)
              }}
              disabled={isBusy}
              aria-label={`Block ${peerName}`}
              title="Block"
            >
              <Ban />
            </button>
          </>
        ) : (
          <button
            type="button"
            onClick={() => void relationships.cancelRequest(request.id)}
            disabled={isBusy}
            aria-label={`Cancel request to ${peerName}`}
            title="Cancel request"
          >
            <X />
          </button>
        )}
      </div>
    </article>
  )
}

function AddFriendModal({
  relationships,
  onClose,
}: {
  relationships: FriendRelationships
  onClose: () => void
}) {
  const [search, setSearch] = useState('')
  const dialogRef = useRef<HTMLElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)
  const normalizedSearch = search.trim().toLowerCase()
  const entries = relationships.directoryEntries.filter(
    (entry) =>
      !normalizedSearch ||
      entry.displayName.toLowerCase().includes(normalizedSearch) ||
      entry.sharedStudyServerName.toLowerCase().includes(normalizedSearch),
  )

  useEffect(() => {
    searchRef.current?.focus()

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab') return

      const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])',
      )
      if (!focusable?.length) return

      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return (
    <div className="v2-modal-backdrop" role="presentation">
      <section
        ref={dialogRef}
        className="add-friend-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="add-friend-title"
      >
        <button type="button" className="modal-close" onClick={onClose} aria-label="Close">
          <X />
        </button>
        <UserPlus />
        <h2 id="add-friend-title">Add a friend</h2>
        <p>Connect with someone from a shared Study Server.</p>
        <input
          ref={searchRef}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search co-members"
          aria-label="Search co-members"
        />

        <div className="add-friend-results">
          {relationships.isLoading ? <p>Loading co-members…</p> : null}
          {relationships.error ? (
            <p className="modal-error" role="alert">
              {relationships.error}
            </p>
          ) : null}
          {!relationships.isLoading && !relationships.error && entries.length === 0 ? (
            <p>No matching co-members.</p>
          ) : null}
          {entries.map((entry) => (
            <article key={entry.userId}>
              <V2Avatar name={entry.displayName} tone={avatarTone(entry.userId)} size="md" />
              <span>
                <strong>{entry.displayName}</strong>
                <small>{entry.sharedStudyServerName}</small>
              </span>
              {entry.state === 'available' ? (
                <button
                  type="button"
                  onClick={() => void relationships.sendRequest(entry.userId)}
                  disabled={relationships.busyActionIds.has(entry.userId)}
                  aria-label={`Send friend request to ${entry.displayName}`}
                >
                  Send
                </button>
              ) : (
                <b className={`relationship-state ${entry.state}`}>
                  {relationshipStateLabel(entry.state)}
                </b>
              )}
            </article>
          ))}
        </div>
        {relationships.actionError ? (
          <p className="modal-error" role="alert">
            {relationships.actionError}
          </p>
        ) : null}
      </section>
    </div>
  )
}

type FriendsHook = ReturnType<typeof useFriendsHub>

function CallModal({ hub, friendName }: { hub: FriendsHook; friendName: string }) {
  const incoming = hub.callState.phase === 'incoming_ringing'
  const inCall = hub.callState.phase === 'in_call'
  return (
    <div className="v2-modal-backdrop">
      <section
        className="dm-call-modal"
        role="dialog"
        aria-modal="true"
        aria-label={`Voice call with ${friendName}`}
      >
        <V2Avatar name={friendName} tone="blue" size="lg" online />
        <h2>{friendName}</h2>
        <p>
          {incoming
            ? 'Incoming call…'
            : inCall
              ? 'Connected'
              : hub.callState.phase === 'connecting'
                ? 'Connecting audio…'
                : 'Ringing…'}
        </p>
        {hub.callError ? (
          <p className="modal-error" role="alert">
            {hub.callError}
          </p>
        ) : null}
        <div>
          {incoming ? (
            <button
              type="button"
              className="accept"
              onClick={hub.acceptCall}
              aria-label="Accept voice call"
            >
              <Phone />
            </button>
          ) : null}
          {inCall ? (
            <button
              type="button"
              onClick={() => void hub.toggleCallMute()}
              aria-label={hub.isMuted ? 'Unmute microphone' : 'Mute microphone'}
            >
              {hub.isMuted ? <MicOff /> : <Mic />}
            </button>
          ) : null}
          <button
            type="button"
            className="hangup"
            onClick={inCall ? hub.hangUpCall : hub.declineCall}
            aria-label={inCall ? 'Hang up voice call' : 'Decline voice call'}
          >
            <Phone />
          </button>
        </div>
      </section>
    </div>
  )
}

function avatarTone(userId: string): AvatarTone {
  const tones: AvatarTone[] = ['blue', 'purple', 'amber', 'green']
  const hash = [...userId].reduce((total, character) => total + character.charCodeAt(0), 0)
  return tones[hash % tones.length]
}

function relationshipStateLabel(state: FriendRelationships['directoryEntries'][number]['state']) {
  switch (state) {
    case 'friend':
      return 'Friends'
    case 'incoming':
      return 'Incoming'
    case 'outgoing':
      return 'Pending'
    case 'blocked':
      return 'Blocked'
    default:
      return 'Available'
  }
}

function formatTime(timestamp: string): string {
  return new Intl.DateTimeFormat(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(timestamp))
}
