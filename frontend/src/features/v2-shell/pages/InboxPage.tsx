import { useMemo, useState, type FormEvent } from 'react'
import { Check, ExternalLink, FileText, Send, Sparkles } from 'lucide-react'

import { V2Avatar } from '../components/V2Avatar'

type InboxFilter = 'All' | 'Mentions' | 'Announcements'

const threads = [
  { id: 'math-answer', course: 'MATH 201', title: 'Your question was answered', time: '1h', kind: 'Mentions', tone: 'blue' as const },
  { id: 'cs-announcement', course: 'CS 101', title: 'Course announcements', time: '3h', kind: 'Announcements', tone: 'blue' as const },
  { id: 'bio-resource', course: 'BIO 150', title: 'New resource uploaded', time: 'yesterday', kind: 'All', tone: 'green' as const },
  { id: 'econ-ta', course: 'ECON 210', title: 'TA replied to queue', time: 'yesterday', kind: 'Mentions', tone: 'purple' as const },
]

export function InboxPage() {
  const [filter, setFilter] = useState<InboxFilter>('All')
  const [selectedId, setSelectedId] = useState('math-answer')
  const [draft, setDraft] = useState('')
  const [replies, setReplies] = useState<string[]>([])
  const [done, setDone] = useState(false)

  const visibleThreads = useMemo(
    () => filter === 'All' ? threads : threads.filter((thread) => thread.kind === filter),
    [filter],
  )

  const submitReply = (event: FormEvent) => {
    event.preventDefault()
    const body = draft.trim()
    if (!body) return
    setReplies((current) => [...current, body])
    setDraft('')
  }

  return (
    <section className="v2-workspace-page inbox-page" aria-label="Inbox">
      <aside className="inbox-thread-pane">
        <h1>Inbox</h1>
        <div className="v2-chip-row" role="tablist" aria-label="Inbox filters">
          {(['All', 'Mentions', 'Announcements'] as InboxFilter[]).map((item) => (
            <button key={item} type="button" className={filter === item ? 'active' : undefined} onClick={() => setFilter(item)}>{item}</button>
          ))}
        </div>
        <p className="v2-section-label">Today</p>
        <div className="inbox-thread-list">
          {visibleThreads.slice(0, 3).map((thread) => (
            <button key={thread.id} type="button" className={selectedId === thread.id ? 'active' : undefined} onClick={() => setSelectedId(thread.id)}>
              <i className={`thread-dot ${thread.tone}`} />
              <span><strong>[{thread.course}]</strong> {thread.title}</span>
              <time>{thread.time}</time>
            </button>
          ))}
        </div>
        <p className="v2-section-label yesterday">Yesterday</p>
        <div className="inbox-thread-list">
          {visibleThreads.slice(3).map((thread) => (
            <button key={thread.id} type="button" className={selectedId === thread.id ? 'active' : undefined} onClick={() => setSelectedId(thread.id)}>
              <i className={`thread-dot ${thread.tone}`} />
              <span><strong>[{thread.course}]</strong> {thread.title}</span>
              <time>{thread.time}</time>
            </button>
          ))}
        </div>
      </aside>

      <div className="inbox-reading-pane">
        <header className="reading-header">
          <div><h2>Merge Sort complexity</h2><p><span>CS 101</span> · Questions</p></div>
          <div>
            <button type="button" className="v2-outline-button"><ExternalLink size={17} /> Open in course</button>
            <button type="button" className={done ? 'v2-success-button' : 'v2-outline-button'} onClick={() => setDone((current) => !current)}><Check size={18} /> {done ? 'Done' : 'Mark done'}</button>
          </div>
        </header>

        <div className="reading-thread">
          <article className="inbox-message-card compact">
            <V2Avatar name="Sam" tone="amber" size="lg" />
            <div><p className="message-author"><strong>Sam</strong><time>2h</time></p><p>Why is Merge Sort <i>O(n log n)</i>?</p></div>
          </article>
          <article className="inbox-message-card ai-message">
            <span className="ai-avatar"><Sparkles size={28} /></span>
            <div>
              <p className="message-author"><strong>AI Study Assistant</strong><b>AI</b><time>2h</time></p>
              <p>Merge Sort splits the array into two halves recursively until subarrays of size 1, then merges them back. The recursion tree has log₂ n levels, and each level does O(n) total work for merging, giving O(n log n) overall.</p>
              <div className="source-links"><button type="button"><FileText size={18} /> CLRS §2.3.1</button><button type="button"><FileText size={18} /> MIT 6.006 Lec 5</button></div>
            </div>
          </article>
          <article className="inbox-message-card compact">
            <V2Avatar name="Alex R" tone="green" size="lg" />
            <div><p className="message-author"><strong>Alex R.</strong><time>1h</time></p><p>The recursion tree view finally made it click for me — each level does O(n) work.</p></div>
          </article>
          {replies.map((reply, index) => (
            <article className="inbox-message-card compact own" key={`${reply}-${index}`}>
              <V2Avatar name="You" tone="blue" size="lg" />
              <div><p className="message-author"><strong>You</strong><time>now</time></p><p>{reply}</p></div>
            </article>
          ))}
        </div>

        <form className="v2-composer" onSubmit={submitReply}>
          <input aria-label="Reply to thread" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Reply…" />
          <button type="submit" aria-label="Send reply" disabled={!draft.trim()}><Send size={22} /></button>
        </form>
      </div>
    </section>
  )
}
