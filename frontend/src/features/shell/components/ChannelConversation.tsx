import { type FormEvent, useState, type ReactNode } from 'react'
import { useLocation, useParams } from 'react-router-dom'

import { useAuthStore } from '../../../stores/auth-store'

import { QuestionsChannelGate } from './QuestionsChannelConversation'
import { ResourcesChannelGate } from './ResourcesChannelConversation'
import { VoiceChannelPanel } from '../../voice/components/VoiceChannelPanel'
import { ChannelHeader } from './ChannelHeader'
import { useChannelConversation } from '../hooks/use-channel-conversation'
import { useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import {
  channelBreadcrumb,
  channelDescription,
  findChannelLabel,
  findCourseChannelContext,
  findStudyChannel,
  isQuestionsChannel,
  isResourcesChannel,
  isVoiceStudyChannel,
} from '../shell-routes'
import type { ChannelScope } from '../channel-message-types'

export function ChannelConversation() {
  const { serverId, channelId } = useParams()
  const location = useLocation()
  const channelScope = resolveChannelScope(location.pathname)

  if (!channelScope || !channelId) {
    return (
      <ConversationFrame title="Select a channel">
        <p className="text-sm text-app-muted">
          Pick a study server channel or a course channel from the sidebar.
        </p>
      </ConversationFrame>
    )
  }

  return <ChannelConversationPanel key={`${channelScope}:${channelId}`} serverId={serverId} channelId={channelId} channelScope={channelScope} />
}

function ChannelConversationPanel({
  serverId,
  channelId,
  channelScope,
}: {
  serverId: string | undefined
  channelId: string
  channelScope: ChannelScope
}) {
  const navigationQuery = useStudyServerNavigationQuery(serverId)

  if (navigationQuery.isLoading) {
    return (
      <ConversationFrame title="Loading channel…">
        <p className="text-sm text-app-muted">Checking your access to this channel.</p>
      </ConversationFrame>
    )
  }

  if (navigationQuery.isError) {
    return (
      <ConversationFrame title="Study server unavailable">
        <p className="text-sm text-app-muted">
          You do not have access to this study server or it no longer exists.
        </p>
      </ConversationFrame>
    )
  }

  const courseChannelContext =
    channelScope === 'course' ? findCourseChannelContext(navigationQuery.data, channelId) : null
  if (isQuestionsChannel(courseChannelContext)) {
    return <QuestionsChannelGate serverId={serverId} channelId={channelId} />
  }
  if (isResourcesChannel(courseChannelContext)) {
    return <ResourcesChannelGate serverId={serverId} channelId={channelId} />
  }

  if (channelScope === 'study' && isVoiceStudyChannel(navigationQuery.data, channelId)) {
    const studyChannel = findStudyChannel(navigationQuery.data, channelId)
    if (studyChannel) {
      return <VoiceChannelPanel channelId={channelId} channelLabel={studyChannel.name} />
    }
  }

  const channelLabel = findChannelLabel(navigationQuery.data, channelScope, channelId)
  if (!channelLabel) {
    return (
      <ConversationFrame title="Channel unavailable">
        <p className="text-sm text-app-muted">
          This channel was not found or you do not have permission to open it.
        </p>
      </ConversationFrame>
    )
  }

  return (
    <LiveChannelConversation
      channelId={channelId}
      channelScope={channelScope}
      navigation={navigationQuery.data!}
    />
  )
}

function LiveChannelConversation({
  channelId,
  channelScope,
  navigation,
}: {
  channelId: string
  channelScope: ChannelScope
  navigation: NonNullable<ReturnType<typeof useStudyServerNavigationQuery>['data']>
}) {
  const currentUserId = useAuthStore((state) => state.user?.id)
  const conversation = useChannelConversation(channelScope, channelId)
  const [draft, setDraft] = useState('')
  const breadcrumb = channelBreadcrumb(navigation, channelScope, channelId)

  if (!breadcrumb) {
    return null
  }

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void conversation.sendMessage(draft).then((sent) => {
      if (sent) {
        setDraft('')
      }
    })
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <ChannelHeader
        channelName={breadcrumb.channelName}
        courseTitle={breadcrumb.courseTitle}
        description={channelDescription(channelScope, breadcrumb.channelName)}
        trailing={<ConnectionBadge status={conversation.connectionStatus} />}
      />

      <div className="flex min-h-0 flex-1 flex-col">
        {conversation.isLoadingHistory ? (
          <p className="p-4 text-sm text-app-muted">Loading message history…</p>
        ) : null}

        {conversation.error ? (
          <p className="border-b border-rose-500/30 bg-rose-500/10 px-4 py-2 text-sm text-rose-200">
            {conversation.error}
          </p>
        ) : null}

        <ol className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
          {conversation.messages.length === 0 ? (
            <li className="text-sm text-app-muted">No messages yet. Say hello to the channel.</li>
          ) : (
            conversation.messages.map((message) => (
              <li key={message.id} className="rounded-lg border border-app-border bg-app-surface px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-xs text-app-muted">
                  <span>{message.senderUserId === currentUserId ? 'You' : message.senderUserId.slice(0, 8)}</span>
                  <time dateTime={message.createdAt}>{formatTimestamp(message.createdAt)}</time>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-sm text-app-text">{message.body}</p>
              </li>
            ))
          )}
        </ol>

        <form className="border-t border-app-border p-4" onSubmit={onSubmit}>
          <label className="sr-only" htmlFor="channel-message-input">
            Message #{breadcrumb.channelName}
          </label>
          <div className="flex gap-2">
            <input
              id="channel-message-input"
              className="min-w-0 flex-1 rounded-md border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text outline-none ring-app-accent focus:ring-2"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder={`Message #${breadcrumb.channelName}`}
              disabled={conversation.isSending || conversation.connectionStatus === 'connecting'}
            />
            <button
              type="submit"
              className="rounded-md bg-app-accent px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
              disabled={
                conversation.isSending ||
                conversation.connectionStatus === 'connecting' ||
                draft.trim().length === 0
              }
            >
              {conversation.isSending ? 'Sending…' : 'Send'}
            </button>
          </div>
        </form>
      </div>
    </section>
  )
}

function ConversationFrame({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <div className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Conversation
        </p>
        <h2 className="mt-1 text-base font-semibold text-app-text">{title}</h2>
      </div>
      <div className="flex flex-1 items-center justify-center p-6">{children}</div>
    </section>
  )
}

function ConnectionBadge({ status }: { status: string }) {
  const label =
    status === 'connected'
      ? 'Live'
      : status === 'reconnecting'
        ? 'Reconnecting…'
        : status === 'connecting'
          ? 'Connecting…'
          : 'Offline'

  return (
    <span className="rounded-full border border-app-border px-2 py-0.5 text-xs text-app-muted">
      {label}
    </span>
  )
}

function resolveChannelScope(pathname: string): ChannelScope | null {
  if (pathname.includes('/course-channels/')) {
    return 'course'
  }
  if (pathname.includes('/study-channels/')) {
    return 'study'
  }
  return null
}

function formatTimestamp(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}
