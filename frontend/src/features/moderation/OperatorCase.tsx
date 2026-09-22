import { useState, type FormEvent } from 'react'
import type { ApiFetchInit } from '../../lib/api-client'
import { sourceNames, type Report, type SourceType } from './moderation-api'

type Restriction = { id: string; type: SourceType; targetId: string; reason: string; startsAt: string; expiresAt: string; revokedAt: string | null }
export type CaseDetail = { report: Report; reporterId: string; assignedTo: string | null; evidence: { type: SourceType; id: string; authorId: string | null; title: string; excerpt: string; sourceFingerprint: string | null }; notes: { id: string; actorId: string; body: string; createdAt: string }[]; restrictions: Restriction[] }
export type PrivilegedRequest = <T>(path: string, init?: ApiFetchInit) => Promise<T>

export function OperatorCase({ detail, operator, reason, request, reload, close }: { detail: CaseDetail; operator: { userId: string; role: string }; reason: string; request: PrivilegedRequest; reload: () => Promise<void>; close: () => void }) {
  const [note, setNote] = useState('')
  const [actionReason, setActionReason] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [target, setTarget] = useState(`${detail.evidence.type}:${detail.evidence.id}`)
  const [hours, setHours] = useState('24')
  const [resolution, setResolution] = useState('')
  const [status, setStatus] = useState('RESOLVED')
  const [assignee, setAssignee] = useState(detail.assignedTo ?? operator.userId)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [restoring, setRestoring] = useState<Restriction | null>(null)
  const [operationId, setOperationId] = useState(() => crypto.randomUUID())
  const [type, targetId] = restoring ? [restoring.type, restoring.targetId] : target.split(':')
  const canRestrict = operator.role === 'ADMIN' || !['USER', 'STUDY_SERVER'].includes(type)

  async function mutate(path: string, body: object, success: string) {
    setBusy(true); setError(''); setNotice('')
    try {
      await request(`/reports/${detail.report.id}/${path}`, { method: 'POST', body: JSON.stringify(body) })
      setNotice(success); setNote(''); setConfirmation(''); setRestoring(null)
      if (path === 'restrictions') setOperationId(crypto.randomUUID())
      await reload()
    } catch { setError('The action was not confirmed. Check the case, reason and exact target, then reload before retrying.') }
    finally { setBusy(false) }
  }
  function enforce(event: FormEvent) {
    event.preventDefault()
    if (restoring) void mutate(`restrictions/${restoring.id}/reinstatement`, { reason: actionReason, confirmation }, 'Restriction lifted.')
    else void mutate('restrictions', { operationId, type, targetId, reason: actionReason, confirmation, expiresAt: new Date(Date.now() + Number(hours) * 3600000).toISOString() }, 'Restriction saved.')
  }

  return <section className="operator-case" aria-label="Selected report">
    <button className="operator-back" onClick={close}>Back to reports</button>
    <header><h2>{detail.evidence.title || sourceNames[detail.evidence.type]}</h2><p>{detail.report.status.toLowerCase()} report</p><small>Report reference {detail.report.id}</small></header>
    {error && <p role="alert" className="inline-error">{error}</p>}{notice && <p role="status" className="moderation-notice">{notice}</p>}
    <h3>Report reason</h3><p>{detail.report.reason}</p>
    <h3>Preserved evidence</h3><blockquote>{detail.evidence.excerpt || 'This source has no text excerpt.'}</blockquote>
    <p className="operator-reference">Source {detail.evidence.id}</p>
    {detail.evidence.sourceFingerprint && <details><summary>Source fingerprint</summary><code>{detail.evidence.sourceFingerprint}</code></details>}
    <form className="moderation-form" onSubmit={event => { event.preventDefault(); void mutate('notes', { body: note, reason }, 'Internal note saved.') }}>
      <h3>Internal notes</h3>{detail.notes.map(item => <article key={item.id}><p>{item.body}</p><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString()}</time></article>)}
      <label>New internal note<textarea required maxLength={4000} value={note} onChange={event => setNote(event.target.value)} /></label><button type="submit" disabled={busy || !note.trim()}>Save internal note</button>
    </form>
    {operator.role === 'ADMIN' && <form className="moderation-form" onSubmit={event => { event.preventDefault(); void mutate('assignment', { assignee, reason }, 'Report assigned.') }}>
      <label>Assigned operator reference<input required value={assignee} onChange={event => setAssignee(event.target.value)} /></label><button type="submit" disabled={busy}>Assign report</button>
    </form>}
    <h3>Restrictions</h3>{!detail.restrictions.length ? <p>No restrictions on this case.</p> : <ul className="moderation-list">{detail.restrictions.map(item => <li key={item.id}><strong>{sourceNames[item.type]}</strong><p>{item.reason}</p><p>{item.revokedAt ? 'Lifted' : `Scheduled end ${new Date(item.expiresAt).toLocaleString()}`}</p>{!item.revokedAt && (operator.role === 'ADMIN' || !['USER', 'STUDY_SERVER'].includes(item.type)) && <button onClick={() => { setRestoring(item); setConfirmation('') }}>Review reinstatement</button>}</li>)}</ul>}
    <form className="moderation-form" onSubmit={enforce}>
      <h3>{restoring ? 'Lift restriction' : 'Apply a restriction'}</h3>
      {!restoring && <><label>Target<select value={target} onChange={event => { setTarget(event.target.value); setConfirmation('') }}><option value={`${detail.evidence.type}:${detail.evidence.id}`}>{sourceNames[detail.evidence.type]} in this case</option>{operator.role === 'ADMIN' && detail.evidence.authorId && detail.evidence.type !== 'USER' && <option value={`USER:${detail.evidence.authorId}`}>Author account</option>}</select></label>
        <label>Duration in hours<input type="number" required min="1" max="720" value={hours} onChange={event => setHours(event.target.value)} /></label></>}
      <label>Public action reason<textarea required maxLength={2000} value={actionReason} onChange={event => setActionReason(event.target.value)} /></label>
      <p>{restoring ? 'This lifts only the selected restriction. Other restrictions remain active.' : 'This restricts access immediately. The affected account receives the reason and an appeal path.'}</p>
      <label>Confirm target reference <code>{targetId}</code><input required value={confirmation} onChange={event => setConfirmation(event.target.value)} autoComplete="off" /></label>
      <button type="submit" disabled={busy || !canRestrict || confirmation !== targetId || !actionReason.trim() || (!restoring && detail.report.status === 'RESOLVED')}>{restoring ? 'Lift restriction' : 'Apply restriction'}</button>
      {restoring && <button type="button" onClick={() => { setRestoring(null); setConfirmation('') }}>Cancel reinstatement</button>}
    </form>
    <form className="moderation-form" onSubmit={event => { event.preventDefault(); void mutate('resolution', { status, resolution, reason }, 'Report review saved.') }}>
      <label>Review outcome<select value={status} onChange={event => setStatus(event.target.value)}><option value="RESOLVED">Resolve report</option><option value="ESCALATED">Escalate report</option></select></label>
      <label>Resolution for the reporter<textarea required maxLength={2000} value={resolution} onChange={event => setResolution(event.target.value)} /></label><button type="submit" disabled={busy || !resolution.trim()}>Save review outcome</button>
    </form>
  </section>
}
