import { useMemo, useState, type FormEvent } from 'react'
import { ChevronDown, Hash, MicOff, Plus, Radio, Send, Smile, Volume2 } from 'lucide-react'

import { useChannelConversation } from '../../../shell/hooks/use-channel-conversation'
import { useAuthStore } from '../../../../stores/auth-store'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

const demoMessages = [
  { id: 'demo-1', name: 'Dr. Alex Johnson', role: 'OWNER', time: '2:14 PM', body: 'Welcome to week 3! Problem set is posted in #resources.', tone: 'blue' as const },
  { id: 'demo-2', name: 'Maria Gonzalez', role: 'TA', time: '2:16 PM', body: 'Office hours moved to 3 PM today.', tone: 'green' as const },
  { id: 'demo-3', name: 'Maria Gonzalez', role: 'TA', time: '2:17 PM', body: 'See you all there!', tone: 'green' as const },
]

export function CourseChatPage() {
  const { course, courseCapabilities } = useV2CourseWorkspace()
  const textChannels = useMemo(
    () => course.channels.filter((channel) => channel.kind === 'TEXT' && !['questions', 'resources'].includes(channel.name.toLowerCase())),
    [course.channels],
  )
  const voiceChannels = course.channels.filter((channel) => channel.kind === 'VOICE')
  const [selectedChannelId, setSelectedChannelId] = useState(textChannels[0]?.id)
  const selectedChannel = textChannels.find((channel) => channel.id === selectedChannelId) ?? textChannels[0]
  const conversation = useChannelConversation('course', selectedChannel?.id)
  const currentUserId = useAuthStore((state) => state.user?.id)
  const [draft, setDraft] = useState('')

  const submit = (event: FormEvent) => {
    event.preventDefault()
    void conversation.sendMessage(draft).then((sent) => sent && setDraft(''))
  }

  return (
    <div className="course-chat-layout">
      <aside className="channel-panel">
        <section>
          <header><span>CHANNELS</span><ChevronDown />{courseCapabilities.canManageCourse ? <button type="button" aria-label="Add text channel"><Plus /></button> : null}</header>
          <div className="channel-list">
            {(textChannels.length ? textChannels : [{ id: 'general-demo', name: 'general', kind: 'TEXT' as const }, { id: 'study-demo', name: 'study-group', kind: 'TEXT' as const }, { id: 'memes-demo', name: 'memes', kind: 'TEXT' as const }]).map((channel, index) => (
              <button key={channel.id} type="button" className={(selectedChannel?.id ?? 'general-demo') === channel.id ? 'active' : undefined} onClick={() => setSelectedChannelId(channel.id)}>
                <Hash /><span>{channel.name}</span>{index === 1 ? <b>2</b> : null}
              </button>
            ))}
          </div>
        </section>
        <section className="voice-section">
          <header><span>VOICE</span><ChevronDown />{courseCapabilities.canManageCourse ? <button type="button" aria-label="Add voice channel"><Plus /></button> : null}</header>
          {(voiceChannels.length ? voiceChannels : [{ id: 'voice-demo', name: 'Study Room', kind: 'VOICE' as const }]).map((channel) => (
            <button className="voice-channel-row" type="button" key={channel.id}><Volume2 /><span>{channel.name}</span><span className="voice-avatars"><V2Avatar name="Alex" size="sm" tone="amber" /><V2Avatar name="Maria" size="sm" tone="green" /><V2Avatar name="Sam" size="sm" tone="blue" /></span></button>
          ))}
        </section>
      </aside>

      <section className="chat-panel">
        <div className="chat-message-list">
          {conversation.error ? <p className="inline-error">{conversation.error}</p> : null}
          {conversation.messages.length === 0 ? demoMessages.map((message) => (
            <ChatMessage key={message.id} {...message} />
          )) : conversation.messages.map((message, index) => (
            <ChatMessage key={message.id} name={message.senderUserId === currentUserId ? 'You' : `Classmate ${index + 1}`} role="" time={new Date(message.createdAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })} body={message.body} tone={message.senderUserId === currentUserId ? 'blue' : 'purple'} />
          ))}
        </div>
        <form className="chat-composer" onSubmit={submit}>
          <button type="button" aria-label="Add attachment"><Plus /></button>
          <input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={`Message #${selectedChannel?.name ?? 'general'}`} aria-label={`Message ${selectedChannel?.name ?? 'general'}`} />
          <button type="button" aria-label="Add emoji"><Smile /></button>
          <button type="submit" className="send-button" aria-label="Send message" disabled={!draft.trim() || conversation.isSending}><Send /></button>
        </form>
      </section>
      <span className="sr-only"><Radio /> <MicOff /> Realtime course chat</span>
    </div>
  )
}

function ChatMessage({ name, role, time, body, tone }: { name: string; role: string; time: string; body: string; tone: 'blue' | 'green' | 'purple' }) {
  return (
    <article className="course-chat-message">
      <V2Avatar name={name} tone={tone} size="lg" online />
      <div><p><strong>{name}</strong>{role ? <b className={role === 'TA' ? 'ta' : undefined}>{role}</b> : null}<time>{time}</time></p><span>{body}</span></div>
    </article>
  )
}
