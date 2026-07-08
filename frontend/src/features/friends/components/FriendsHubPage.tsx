import { type FormEvent, useState } from 'react'
import { Link } from 'react-router-dom'

import { useAuthStore } from '../../../stores/auth-store'
import { cn } from '../../../lib/cn'

import { useFriendsHub } from '../hooks/use-friends-hub'
import type { FriendPresenceStatus } from '../types'
import type { SocialRealtimeConnectionStatus } from '../social-realtime-client'

export function FriendsHubPage() {
  const user = useAuthStore((state) => state.user)
  const hub = useFriendsHub()
  const [draft, setDraft] = useState('')

  const selectedFriend = hub.friends.find((friend) => friend.friendUserId === hub.selectedFriendId)

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const submitted = draft
    void hub.sendMessage(submitted).then((sent) => {
      if (sent) {
        setDraft((current) => (current === submitted ? '' : current))
      }
    })
  }

  return (
    <div className="flex min-h-0 flex-1">
      <aside className="flex w-72 shrink-0 flex-col border-r border-app-border bg-app-elevated">
        <div className="border-b border-app-border px-4 py-3">
          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">Friends</p>
          <h1 className="mt-1 text-base font-semibold text-app-text">Direct Messages</h1>
          <p className="mt-1 text-xs text-app-muted">
            Signed in as {user?.displayName ?? 'your account'}
          </p>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-2">
          {hub.isLoadingFriends ? (
            <p className="px-2 py-3 text-sm text-app-muted">Loading friends…</p>
          ) : hub.friends.length === 0 ? (
            hub.friendsListError ? (
              <p className="px-2 py-3 text-sm text-rose-200" role="alert">
                {hub.friendsListError}
              </p>
            ) : (
              <p className="px-2 py-3 text-sm text-app-muted">
                Accepted friends appear here after you share a Study Server and connect on{' '}
                <Link to="/app" className="text-app-accent hover:underline">
                  your courses
                </Link>
                .
              </p>
            )
          ) : (
            <ul className="space-y-1">
              {hub.friends.map((friend) => (
                <li key={friend.friendUserId}>
                  <button
                    type="button"
                    onClick={() => hub.selectFriend(friend.friendUserId)}
                    aria-current={hub.selectedFriendId === friend.friendUserId ? 'true' : undefined}
                    className={cn(
                      'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition-colors',
                      hub.selectedFriendId === friend.friendUserId
                        ? 'bg-app-surface text-app-text'
                        : 'text-app-muted hover:bg-app-surface hover:text-app-text',
                    )}
                  >
                    <PresenceDot status={hub.presenceByFriendId[friend.friendUserId] ?? 'offline'} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">
                        {formatFriendLabel(friend.friendUserId)}
                      </span>
                      <span className="block text-xs capitalize text-app-muted">
                        {hub.presenceByFriendId[friend.friendUserId] ?? 'offline'}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
        {selectedFriend ? (
          <>
            <header className="flex items-center justify-between gap-3 border-b border-app-border px-4 py-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
                  Conversation
                </p>
                <h2 className="mt-1 text-base font-semibold text-app-text">
                  {formatFriendLabel(selectedFriend.friendUserId)}
                </h2>
              </div>
              <div className="flex items-center gap-2">
                <ConnectionBadge status={hub.connectionStatus} />
                <button
                  type="button"
                  disabled
                  title="DM voice lands in issue #32"
                  className="rounded-md border border-app-border px-3 py-1.5 text-sm text-app-muted"
                >
                  Call
                </button>
              </div>
            </header>

            {hub.error ? (
              <p
                className="border-b border-rose-500/30 bg-rose-500/10 px-4 py-2 text-sm text-rose-200"
                role="alert"
              >
                {hub.error}
              </p>
            ) : null}

            <ol className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-4">
              {hub.isLoadingMessages ? (
                <li className="text-sm text-app-muted">Loading message history…</li>
              ) : hub.messages.length === 0 ? (
                <li className="text-sm text-app-muted">No messages yet. Say hello.</li>
              ) : (
                hub.messages.map((message) => (
                  <li
                    key={message.id}
                    className="rounded-lg border border-app-border bg-app-surface px-3 py-2"
                  >
                    <div className="flex items-center justify-between gap-2 text-xs text-app-muted">
                      <span>{message.senderUserId === user?.id ? 'You' : formatFriendLabel(message.senderUserId)}</span>
                      <time dateTime={message.sentAt}>{formatTimestamp(message.sentAt)}</time>
                    </div>
                    <p className="mt-1 whitespace-pre-wrap text-sm text-app-text">{message.body}</p>
                  </li>
                ))
              )}
            </ol>

            <form onSubmit={onSubmit} className="border-t border-app-border p-4">
              <div className="flex gap-2">
                <input
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder={`Message ${formatFriendLabel(selectedFriend.friendUserId)}`}
                  aria-label="Message"
                  className="min-w-0 flex-1 rounded-lg border border-app-border bg-app-elevated px-3 py-2 text-sm text-app-text"
                />
                <button
                  type="submit"
                  disabled={hub.isSending || draft.trim().length === 0}
                  className="rounded-lg bg-app-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  Send
                </button>
              </div>
            </form>
          </>
        ) : (
          <div className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
            Select a friend to open your direct message thread.
          </div>
        )}
      </section>
    </div>
  )
}

function PresenceDot({ status }: { status: FriendPresenceStatus }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'h-2.5 w-2.5 shrink-0 rounded-full',
        status === 'online' ? 'bg-emerald-400' : 'bg-app-border',
      )}
    />
  )
}

function ConnectionBadge({ status }: { status: SocialRealtimeConnectionStatus }) {
  const label =
    status === 'connected'
      ? 'Live'
      : status === 'connecting' || status === 'reconnecting'
        ? 'Connecting'
        : 'Offline'

  return (
    <span className="rounded-full border border-app-border px-2 py-0.5 text-xs text-app-muted">
      {label}
    </span>
  )
}

function formatFriendLabel(userId: string): string {
  return `Friend ${userId.slice(0, 8)}`
}

const messageTimeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: 'numeric',
  minute: '2-digit',
})

function formatTimestamp(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return messageTimeFormatter.format(date)
}
