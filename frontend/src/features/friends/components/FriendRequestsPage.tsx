import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { cn } from '../../../lib/cn'
import { formatUserFacingApiError, isUnauthorizedApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'
import {
  acceptFriendRequest,
  blockUser,
  cancelFriendRequest,
  declineFriendRequest,
} from '../friends-api'
import { formatFriendLabel } from '../friend-label'
import { friendRequestsQueryKey, useFriendRequestsQuery } from '../hooks/use-friend-requests-queries'
import type { FriendRequest } from '../types'

import { FriendsHubSidebar } from './FriendsHubSidebar'

export function FriendRequestsPage() {
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const requestsQuery = useFriendRequestsQuery()
  const [activeTab, setActiveTab] = useState<'incoming' | 'outgoing'>('incoming')
  const [actionError, setActionError] = useState<string | null>(null)
  const [pendingActionId, setPendingActionId] = useState<string | null>(null)

  const incoming = requestsQuery.data?.incoming ?? []
  const outgoing = requestsQuery.data?.outgoing ?? []
  const activeRequests = activeTab === 'incoming' ? incoming : outgoing

  const refreshInbox = async () => {
    await queryClient.invalidateQueries({ queryKey: friendRequestsQueryKey })
  }

  const runAction = async (friendRequestId: string, action: () => Promise<unknown>) => {
    setPendingActionId(friendRequestId)
    setActionError(null)

    try {
      await action()
      await refreshInbox()
    } catch (caught) {
      if (isUnauthorizedApiError(caught)) {
        clearSession()
        return
      }
      setActionError(formatUserFacingApiError(caught, 'Unable to update this friend request.'))
    } finally {
      setPendingActionId(null)
    }
  }

  return (
    <div className="flex min-h-0 flex-1">
      <FriendsHubSidebar />

      <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
        <header className="border-b border-app-border px-6 py-5">
          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
            Friend Requests
          </p>
          <h1 className="mt-2 text-2xl font-semibold text-app-text">Pending Requests</h1>
          <p className="mt-1 text-sm text-app-muted">
            Accept new friends, decline unwanted requests, or cancel requests you sent.
          </p>
        </header>

        <div className="border-b border-app-border px-6">
          <div className="flex gap-4" role="tablist" aria-label="Friend request direction">
            <TabButton
              isActive={activeTab === 'incoming'}
              onClick={() => setActiveTab('incoming')}
              label={`Incoming (${incoming.length})`}
            />
            <TabButton
              isActive={activeTab === 'outgoing'}
              onClick={() => setActiveTab('outgoing')}
              label={`Outgoing (${outgoing.length})`}
            />
          </div>
        </div>

        {actionError ? (
          <p className="border-b border-rose-500/30 bg-rose-500/10 px-6 py-3 text-sm text-rose-200" role="alert">
            {actionError}
          </p>
        ) : null}

        <div className="min-h-0 flex-1 overflow-y-auto px-6 py-4">
          {requestsQuery.isLoading ? (
            <p className="text-sm text-app-muted">Loading friend requests…</p>
          ) : requestsQuery.isError ? (
            <p className="text-sm text-rose-200" role="alert">
              Could not load friend requests.
            </p>
          ) : activeRequests.length === 0 ? (
            <p className="text-sm text-app-muted">
              {activeTab === 'incoming'
                ? 'No incoming friend requests right now.'
                : 'You have not sent any pending friend requests.'}
            </p>
          ) : (
            <ul className="space-y-3">
              {activeRequests.map((request) => (
                <li key={request.id}>
                  <FriendRequestRow
                    request={request}
                    mode={activeTab}
                    isBusy={pendingActionId === request.id}
                    onAccept={() => {
                      void runAction(request.id, () => acceptFriendRequest(request.id))
                    }}
                    onDecline={() => {
                      void runAction(request.id, () => declineFriendRequest(request.id))
                    }}
                    onCancel={() => {
                      void runAction(request.id, () => cancelFriendRequest(request.id))
                    }}
                    onBlock={() => {
                      void runAction(request.id, () =>
                        blockUser(
                          activeTab === 'incoming' ? request.senderUserId : request.recipientUserId,
                        ),
                      )
                    }}
                  />
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  )
}

function TabButton({
  isActive,
  onClick,
  label,
}: {
  isActive: boolean
  onClick: () => void
  label: string
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={isActive}
      onClick={onClick}
      className={cn(
        'border-b-2 px-1 py-3 text-sm font-medium transition-colors',
        isActive
          ? 'border-app-accent text-app-text'
          : 'border-transparent text-app-muted hover:text-app-text',
      )}
    >
      {label}
    </button>
  )
}

function FriendRequestRow({
  request,
  mode,
  isBusy,
  onAccept,
  onDecline,
  onCancel,
  onBlock,
}: {
  request: FriendRequest
  mode: 'incoming' | 'outgoing'
  isBusy: boolean
  onAccept: () => void
  onDecline: () => void
  onCancel: () => void
  onBlock: () => void
}) {
  const peerUserId = mode === 'incoming' ? request.senderUserId : request.recipientUserId

  return (
    <article className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-app-border bg-app-surface px-4 py-3">
      <div>
        <p className="text-sm font-medium text-app-text">{formatFriendLabel(peerUserId)}</p>
        <p className="text-xs text-app-muted">
          {mode === 'incoming' ? 'Wants to connect' : 'Request sent'} ·{' '}
          <time dateTime={request.createdAt}>{formatTimestamp(request.createdAt)}</time>
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {mode === 'incoming' ? (
          <>
            <ActionButton label="Accept" onClick={onAccept} isBusy={isBusy} variant="primary" />
            <ActionButton label="Decline" onClick={onDecline} isBusy={isBusy} />
            <ActionButton label="Block" onClick={onBlock} isBusy={isBusy} variant="danger" />
          </>
        ) : (
          <>
            <ActionButton label="Cancel" onClick={onCancel} isBusy={isBusy} />
            <ActionButton label="Block" onClick={onBlock} isBusy={isBusy} variant="danger" />
          </>
        )}
      </div>
    </article>
  )
}

function ActionButton({
  label,
  onClick,
  isBusy,
  variant = 'default',
}: {
  label: string
  onClick: () => void
  isBusy: boolean
  variant?: 'default' | 'primary' | 'danger'
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={isBusy}
      className={cn(
        'rounded-md px-3 py-1.5 text-sm disabled:opacity-50',
        variant === 'primary'
          ? 'bg-app-accent font-medium text-white hover:bg-app-accent-hover'
          : variant === 'danger'
            ? 'border border-rose-500/40 text-rose-200 hover:bg-rose-500/10'
            : 'border border-app-border text-app-text hover:bg-app-elevated',
      )}
    >
      {label}
    </button>
  )
}

const requestTimeFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
})

function formatTimestamp(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return requestTimeFormatter.format(date)
}
