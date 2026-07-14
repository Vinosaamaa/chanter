import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Archive,
  ChevronDown,
  Hash,
  Mic,
  MicOff,
  Pencil,
  Plus,
  Radio,
  Send,
  Smile,
  Volume2,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'

import {
  archiveCourseChannel,
  createCourseChannel,
  renameCourseChannel,
  type CourseChannelKind,
} from '../../../course-channels/course-channels-api'
import { fetchPublicProfiles } from '../../../friends/friends-api'
import type { PublicUserProfile } from '../../../friends/types'
import { useChannelConversation } from '../../../shell/hooks/use-channel-conversation'
import {
  studyServerNavigationQueryKey,
} from '../../../shell/hooks/use-shell-queries'
import type { ShellChannel } from '../../../shell/types'
import { fetchVoicePresences } from '../../../voice/voice-api'
import { useVoiceChannel } from '../../../voice/hooks/use-voice-channel'
import { useAuthStore } from '../../../../stores/auth-store'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

type ChannelEditorState =
  | { mode: 'create'; kind: CourseChannelKind }
  | { mode: 'rename'; channel: ShellChannel }
  | { mode: 'archive'; channel: ShellChannel }

export function CourseChatPage() {
  const { serverId, course, courseCapabilities, selectedCohort } = useV2CourseWorkspace()
  const currentUserId = useAuthStore((state) => state.user?.id)
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [draft, setDraft] = useState('')
  const [editor, setEditor] = useState<ChannelEditorState | null>(null)
  const [managementError, setManagementError] = useState<string | null>(null)
  const [isManaging, setIsManaging] = useState(false)
  const editorTriggerRef = useRef<HTMLElement | null>(null)

  const cohortChannels = useMemo(
    () => course.channels.filter(
      (channel) => !channel.cohortId || channel.cohortId === selectedCohort?.id,
    ),
    [course.channels, selectedCohort?.id],
  )
  const textChannels = useMemo(
    () => cohortChannels.filter(
      (channel) => channel.kind === 'TEXT'
        && !['questions', 'resources'].includes(channel.name.toLowerCase()),
    ),
    [cohortChannels],
  )
  const voiceChannels = useMemo(
    () => cohortChannels.filter((channel) => channel.kind === 'VOICE'),
    [cohortChannels],
  )
  const requestedChannelId = searchParams.get('channel')
  const selectedChannel = cohortChannels.find((channel) => channel.id === requestedChannelId)
    ?? textChannels[0]
    ?? voiceChannels[0]
  const selectedTextChannel = selectedChannel?.kind === 'TEXT' ? selectedChannel : undefined
  const conversation = useChannelConversation('course', selectedTextChannel?.id)

  useEffect(() => {
    if (selectedChannel?.id === requestedChannelId) return
    const nextParams = new URLSearchParams(searchParams)
    if (selectedChannel) nextParams.set('channel', selectedChannel.id)
    else nextParams.delete('channel')
    setSearchParams(nextParams, { replace: true })
  }, [requestedChannelId, searchParams, selectedChannel, setSearchParams])

  const senderIds = useMemo(
    () => [...new Set(conversation.messages.map((message) => message.senderUserId))].sort(),
    [conversation.messages],
  )
  const profiles = useQuery({
    queryKey: ['course-chat-profiles', senderIds],
    queryFn: () => fetchPublicProfiles(senderIds),
    enabled: senderIds.length > 0,
  })
  const profilesById = useMemo(
    () => new Map((profiles.data?.profiles ?? []).map((profile) => [profile.userId, profile])),
    [profiles.data?.profiles],
  )

  const selectChannel = (channelId: string) => {
    const nextParams = new URLSearchParams(searchParams)
    nextParams.set('channel', channelId)
    setSearchParams(nextParams)
    setDraft('')
  }

  const openEditor = (nextEditor: ChannelEditorState) => {
    editorTriggerRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null
    setEditor(nextEditor)
  }

  const refreshNavigation = async () => {
    await queryClient.invalidateQueries({
      queryKey: studyServerNavigationQueryKey(currentUserId, serverId),
    })
  }

  const submitMessage = (event: FormEvent) => {
    event.preventDefault()
    void conversation.sendMessage(draft).then((sent) => sent && setDraft(''))
  }

  const saveChannel = async (name: string) => {
    if (!editor || !selectedCohort) return
    setIsManaging(true)
    setManagementError(null)
    try {
      if (editor.mode === 'create') {
        const created = await createCourseChannel(selectedCohort.id, { name, kind: editor.kind })
        await refreshNavigation()
        selectChannel(created.id)
      } else if (editor.mode === 'rename') {
        await renameCourseChannel(editor.channel.id, name)
        await refreshNavigation()
      }
      setEditor(null)
    } catch (caught) {
      setManagementError(caught instanceof Error ? caught.message : 'Unable to save channel')
    } finally {
      setIsManaging(false)
    }
  }

  const archiveChannel = async () => {
    if (!editor || editor.mode !== 'archive') return
    setIsManaging(true)
    setManagementError(null)
    try {
      await archiveCourseChannel(editor.channel.id)
      if (selectedChannel?.id === editor.channel.id) {
        const nextParams = new URLSearchParams(searchParams)
        nextParams.delete('channel')
        setSearchParams(nextParams, { replace: true })
      }
      await refreshNavigation()
      setEditor(null)
    } catch (caught) {
      setManagementError(caught instanceof Error ? caught.message : 'Unable to archive channel')
    } finally {
      setIsManaging(false)
    }
  }

  return (
    <div className="course-chat-layout">
      <aside className="channel-panel" inert={editor ? true : undefined} aria-hidden={editor ? true : undefined}>
        <section>
          <ChannelSectionHeader
            label="CHANNELS"
            canManage={courseCapabilities.canManageCourse && Boolean(selectedCohort)}
            onAdd={() => openEditor({ mode: 'create', kind: 'TEXT' })}
            addLabel="Add text channel"
          />
          <div className="course-channel-list">
            {textChannels.length === 0 ? <p className="channel-empty-state">No text channels yet.</p> : null}
            {textChannels.map((channel) => (
              <ChannelRow
                key={channel.id}
                channel={channel}
                selected={selectedChannel?.id === channel.id}
                canManage={courseCapabilities.canManageCourse}
                onSelect={() => selectChannel(channel.id)}
                onRename={() => openEditor({ mode: 'rename', channel })}
                onArchive={() => openEditor({ mode: 'archive', channel })}
              />
            ))}
          </div>
        </section>

        <section className="voice-section">
          <ChannelSectionHeader
            label="VOICE"
            canManage={courseCapabilities.canManageCourse && Boolean(selectedCohort)}
            onAdd={() => openEditor({ mode: 'create', kind: 'VOICE' })}
            addLabel="Add voice channel"
          />
          {voiceChannels.length === 0 ? <p className="channel-empty-state">No voice channels yet.</p> : null}
          {voiceChannels.map((channel) => (
            <CourseVoiceChannelRow
              key={channel.id}
              channel={channel}
              selected={selectedChannel?.id === channel.id}
              canManage={courseCapabilities.canManageCourse}
              onSelect={() => selectChannel(channel.id)}
              onRename={() => openEditor({ mode: 'rename', channel })}
              onArchive={() => openEditor({ mode: 'archive', channel })}
            />
          ))}
        </section>
      </aside>

      <section className="chat-panel" inert={editor ? true : undefined} aria-hidden={editor ? true : undefined}>
        {selectedChannel?.kind === 'VOICE' ? (
          <CourseVoiceWorkspace
            key={`course:${selectedChannel.id}`}
            channel={selectedChannel}
            currentUserId={currentUserId}
          />
        ) : (
          <TextChannelWorkspace
            channel={selectedTextChannel}
            conversation={conversation}
            currentUserId={currentUserId}
            profilesById={profilesById}
            draft={draft}
            setDraft={setDraft}
            onSubmit={submitMessage}
          />
        )}
      </section>

      {editor ? (
        <ChannelEditorModal
          editor={editor}
          busy={isManaging}
          error={managementError}
          onClose={() => {
            setEditor(null)
            setManagementError(null)
          }}
          onSave={saveChannel}
          onArchive={archiveChannel}
          onRestoreFocus={() => editorTriggerRef.current?.focus()}
        />
      ) : null}
      <span className="sr-only"><Radio /> Course chat and voice workspace</span>
    </div>
  )
}

function ChannelSectionHeader({
  label,
  canManage,
  onAdd,
  addLabel,
}: {
  label: string
  canManage: boolean
  onAdd: () => void
  addLabel: string
}) {
  return (
    <header>
      <span>{label}</span>
      <ChevronDown />
      {canManage ? (
        <button type="button" aria-label={addLabel} title={addLabel} onClick={onAdd}>
          <Plus />
        </button>
      ) : null}
    </header>
  )
}

function ChannelRow({
  channel,
  selected,
  canManage,
  onSelect,
  onRename,
  onArchive,
}: {
  channel: ShellChannel
  selected: boolean
  canManage: boolean
  onSelect: () => void
  onRename: () => void
  onArchive: () => void
}) {
  return (
    <div className={`channel-row${selected ? ' active' : ''}`}>
      <button type="button" className="channel-row-main" aria-label={channel.name} onClick={onSelect}>
        <Hash /><span>{channel.name}</span>
      </button>
      {canManage ? <ChannelActions channelName={channel.name} onRename={onRename} onArchive={onArchive} /> : null}
    </div>
  )
}

function CourseVoiceChannelRow({
  channel,
  selected,
  canManage,
  onSelect,
  onRename,
  onArchive,
}: {
  channel: ShellChannel
  selected: boolean
  canManage: boolean
  onSelect: () => void
  onRename: () => void
  onArchive: () => void
}) {
  const presences = useQuery({
    queryKey: ['course-voice-presences', channel.id],
    queryFn: () => fetchVoicePresences(channel.id, 'course'),
    refetchInterval: 10_000,
  })
  const memberIds = useMemo(
    () => [...new Set((presences.data ?? []).map((presence) => presence.memberUserId))].sort(),
    [presences.data],
  )
  const profiles = useQuery({
    queryKey: ['course-voice-profiles', memberIds],
    queryFn: () => fetchPublicProfiles(memberIds),
    enabled: memberIds.length > 0,
  })

  return (
    <div className={`channel-row voice-channel-row${selected ? ' active' : ''}`}>
      <button type="button" className="channel-row-main" aria-label={channel.name} onClick={onSelect}>
        <Volume2 />
        <span>{channel.name}</span>
        <span className="voice-avatars" aria-label={`${memberIds.length} connected`}>
          {(profiles.data?.profiles ?? []).slice(0, 3).map((profile, index) => (
            <V2Avatar key={profile.userId} name={profile.displayName} size="sm" tone={avatarTone(index)} />
          ))}
          {memberIds.length > 3 ? <b>+{memberIds.length - 3}</b> : null}
        </span>
      </button>
      {canManage ? <ChannelActions channelName={channel.name} onRename={onRename} onArchive={onArchive} /> : null}
    </div>
  )
}

function ChannelActions({
  channelName,
  onRename,
  onArchive,
}: {
  channelName: string
  onRename: () => void
  onArchive: () => void
}) {
  return (
    <span className="channel-row-actions">
      <button type="button" aria-label={`Rename ${channelName}`} title="Rename channel" onClick={onRename}>
        <Pencil />
      </button>
      <button type="button" aria-label={`Archive ${channelName}`} title="Archive channel" onClick={onArchive}>
        <Archive />
      </button>
    </span>
  )
}

function TextChannelWorkspace({
  channel,
  conversation,
  currentUserId,
  profilesById,
  draft,
  setDraft,
  onSubmit,
}: {
  channel: ShellChannel | undefined
  conversation: ReturnType<typeof useChannelConversation>
  currentUserId: string | undefined
  profilesById: Map<string, PublicUserProfile>
  draft: string
  setDraft: (value: string) => void
  onSubmit: (event: FormEvent) => void
}) {
  const unavailableExplanation = 'This capability is not available yet.'
  return (
    <>
      <div className="chat-message-list">
        {!channel ? (
          <div className="chat-empty-state"><Hash /><h2>No text channel selected</h2><p>Create or select a text channel to start a conversation.</p></div>
        ) : null}
        {channel && conversation.connectionStatus === 'reconnecting' ? (
          <p className="connection-state" role="status">Reconnecting to live messages…</p>
        ) : null}
        {conversation.error ? <p className="inline-error" role="alert">{conversation.error}</p> : null}
        {channel && conversation.isLoadingHistory ? <p className="chat-loading-state">Loading messages…</p> : null}
        {channel && !conversation.isLoadingHistory && conversation.messages.length === 0 ? (
          <div className="chat-empty-state"><Hash /><h2>#{channel.name}</h2><p>No messages yet. Start the conversation.</p></div>
        ) : null}
        {conversation.messages.map((message, index) => {
          const profile = profilesById.get(message.senderUserId)
          const isCurrentUser = message.senderUserId === currentUserId
          const name = isCurrentUser ? 'You' : profile?.displayName ?? 'Member unavailable'
          return (
            <ChatMessage
              key={message.id}
              name={name}
              time={new Date(message.createdAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}
              body={message.body}
              tone={isCurrentUser ? 'blue' : avatarTone(index)}
            />
          )
        })}
      </div>
      <form className="chat-composer" onSubmit={onSubmit}>
        <button
          type="button"
          aria-label="Add attachment"
          aria-disabled="true"
          aria-describedby="course-chat-unavailable-capability"
          title={unavailableExplanation}
        >
          <Plus />
        </button>
        <input
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={channel ? `Message #${channel.name}` : 'Select a text channel to message'}
          aria-label={channel ? `Message ${channel.name}` : 'No text channel selected'}
          disabled={!channel}
          maxLength={4000}
        />
        <button
          type="button"
          aria-label="Add emoji"
          aria-disabled="true"
          aria-describedby="course-chat-unavailable-capability"
          title={unavailableExplanation}
        >
          <Smile />
        </button>
        <span id="course-chat-unavailable-capability" className="sr-only">
          Attachments and emoji are not available yet.
        </span>
        <button
          type="submit"
          className="send-button"
          aria-label="Send message"
          disabled={!channel || !draft.trim() || conversation.isSending}
        >
          <Send />
        </button>
      </form>
    </>
  )
}

function CourseVoiceWorkspace({
  channel,
  currentUserId,
}: {
  channel: ShellChannel
  currentUserId: string | undefined
}) {
  const voice = useVoiceChannel(channel.id, 'course')
  const memberIds = useMemo(
    () => [...new Set(voice.presences.map((presence) => presence.memberUserId))].sort(),
    [voice.presences],
  )
  const profiles = useQuery({
    queryKey: ['course-voice-profiles', memberIds],
    queryFn: () => fetchPublicProfiles(memberIds),
    enabled: memberIds.length > 0,
  })
  const profilesById = new Map((profiles.data?.profiles ?? []).map((profile) => [profile.userId, profile]))
  const connected = voice.status === 'connected'

  return (
    <div className="course-voice-workspace">
      <header>
        <span><Volume2 /></span>
        <div><small>VOICE CHANNEL</small><h2>{channel.name}</h2></div>
        <b className={connected ? 'connected' : undefined}>{connected ? 'Connected' : 'Ready to join'}</b>
      </header>
      {voice.error ? <p className="inline-error" role="alert">{voice.error}</p> : null}
      <div className="course-voice-stage">
        <div className="voice-member-grid">
          {voice.isLoadingPresences ? (
            <div className="chat-empty-state" role="status"><Mic /><h2>Loading voice participants…</h2></div>
          ) : voice.presenceError ? (
            <div className="chat-empty-state"><MicOff /><h2>Voice participants unavailable</h2><p>Try again in a moment.</p></div>
          ) : voice.presences.length === 0 ? (
            <div className="chat-empty-state"><Mic /><h2>The room is quiet</h2><p>Join when you are ready to study together.</p></div>
          ) : voice.presences.map((presence, index) => {
            const name = presence.memberUserId === currentUserId
              ? 'You'
              : profilesById.get(presence.memberUserId)?.displayName ?? 'Member unavailable'
            return (
              <article key={presence.memberUserId}>
                <V2Avatar name={name} tone={avatarTone(index)} size="lg" online />
                <strong>{name}</strong>
                <small>{presence.canSpeak ? 'Can speak' : 'Listening'}</small>
              </article>
            )
          })}
        </div>
      </div>
      <footer className="course-voice-controls">
        {!connected ? (
          <button type="button" className="v2-primary-button" disabled={voice.isBusy || voice.status === 'connecting'} onClick={() => void voice.joinVoice()}>
            <Mic />{voice.status === 'connecting' ? 'Connecting…' : 'Join voice'}
          </button>
        ) : (
          <>
            <button type="button" className={voice.isMuted ? 'active' : undefined} disabled={voice.isBusy} onClick={() => void voice.toggleMute()}>
              {voice.isMuted ? <MicOff /> : <Mic />}{voice.isMuted ? 'Unmute' : 'Mute'}
            </button>
            <button type="button" className="leave" disabled={voice.isBusy} onClick={() => void voice.leaveVoice()}>
              <X />Leave
            </button>
          </>
        )}
      </footer>
    </div>
  )
}

function ChannelEditorModal({
  editor,
  busy,
  error,
  onClose,
  onSave,
  onArchive,
  onRestoreFocus,
}: {
  editor: ChannelEditorState
  busy: boolean
  error: string | null
  onClose: () => void
  onSave: (name: string) => Promise<void>
  onArchive: () => Promise<void>
  onRestoreFocus: () => void
}) {
  const [name, setName] = useState(editor.mode === 'rename' ? editor.channel.name : '')
  const channelKind = editor.mode === 'create' ? editor.kind.toLowerCase() : editor.channel.kind.toLowerCase()
  const dialogRef = useRef<HTMLElement>(null)
  const closeRef = useRef(onClose)
  const busyRef = useRef(busy)
  const restoreFocusRef = useRef(onRestoreFocus)

  useEffect(() => {
    closeRef.current = onClose
    busyRef.current = busy
    restoreFocusRef.current = onRestoreFocus
  }, [busy, onClose, onRestoreFocus])

  useEffect(() => {
    const dialog = dialogRef.current
    const focusableSelector =
      'button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
    const initialFocus = dialog?.querySelector<HTMLElement>('[data-initial-focus]')
      ?? dialog?.querySelector<HTMLElement>(focusableSelector)
    initialFocus?.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (!busyRef.current) {
          event.preventDefault()
          closeRef.current()
        }
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return
      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(focusableSelector),
      )
      if (focusable.length === 0) return
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

    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      window.requestAnimationFrame(() => restoreFocusRef.current())
    }
  }, [])

  if (editor.mode === 'archive') {
    return (
      <div className="v2-modal-backdrop" role="presentation">
        <section ref={(node) => { dialogRef.current = node }} className="course-channel-modal" role="dialog" aria-modal="true" aria-labelledby="archive-channel-title">
          <button type="button" className="modal-close" aria-label="Close" onClick={onClose}><X /></button>
          <h2 id="archive-channel-title">Archive #{editor.channel.name}?</h2>
          <p>Members will no longer see or access this channel. Its message history stays preserved.</p>
          {error ? <p className="modal-error" role="alert">{error}</p> : null}
          <footer>
            <button type="button" className="v2-outline-button" data-initial-focus onClick={onClose}>Cancel</button>
            <button type="button" className="danger-button" disabled={busy} onClick={() => void onArchive()}>{busy ? 'Archiving…' : 'Archive channel'}</button>
          </footer>
        </section>
      </div>
    )
  }

  const title = editor.mode === 'create' ? `Create ${channelKind} channel` : `Rename #${editor.channel.name}`
  return (
    <div className="v2-modal-backdrop" role="presentation">
      <form ref={(node) => { dialogRef.current = node }} className="course-channel-modal" role="dialog" aria-modal="true" aria-labelledby="channel-editor-title" onSubmit={(event) => { event.preventDefault(); void onSave(name) }}>
        <button type="button" className="modal-close" aria-label="Close" onClick={onClose}><X /></button>
        <h2 id="channel-editor-title">{title}</h2>
        <p>{editor.mode === 'create' ? `Add a ${channelKind} space for this Cohort.` : 'Choose a clear name members can recognize.'}</p>
        <label>Channel name<input data-initial-focus required maxLength={80} value={name} onChange={(event) => setName(event.target.value)} placeholder="study-group" /></label>
        {error ? <p className="modal-error" role="alert">{error}</p> : null}
        <footer>
          <button type="button" className="v2-outline-button" onClick={onClose}>Cancel</button>
          <button type="submit" className="v2-primary-button" disabled={busy || !name.trim()}>{busy ? 'Saving…' : editor.mode === 'create' ? 'Create channel' : 'Save name'}</button>
        </footer>
      </form>
    </div>
  )
}

function ChatMessage({
  name,
  time,
  body,
  tone,
}: {
  name: string
  time: string
  body: string
  tone: 'blue' | 'green' | 'purple' | 'amber'
}) {
  return (
    <article className="course-chat-message">
      <V2Avatar name={name} tone={tone} size="lg" online />
      <div><p><strong>{name}</strong><time>{time}</time></p><span>{body}</span></div>
    </article>
  )
}

function avatarTone(index: number): 'blue' | 'green' | 'purple' | 'amber' {
  return (['blue', 'green', 'purple', 'amber'] as const)[index % 4]
}
