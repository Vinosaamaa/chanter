import { useState, type FormEvent } from 'react'
import type { PrivilegedRequest } from './OperatorCase'

type DirectoryItem = { type: 'USER' | 'STUDY_SERVER'; id: string; name: string }
type Appeal = { id: string; restrictionId: string; reportId: string; userId: string; body: string; status: string; resolution: string | null; createdAt: string }
type OperatorRole = { userId: string; role: string; revoked: boolean; factorEnrolled: boolean }

export function OperatorAdminTools({ request, reason, openTarget }: { request: PrivilegedRequest; reason: string; openTarget: (id: string) => void }) {
  const [view, setView] = useState<'directory' | 'appeals' | 'operators'>('directory')
  const [query, setQuery] = useState('')
  const [type, setType] = useState('USER')
  const [offset, setOffset] = useState(0)
  const [items, setItems] = useState<DirectoryItem[] | null>(null)
  const [appeals, setAppeals] = useState<Appeal[] | null>(null)
  const [operators, setOperators] = useState<OperatorRole[] | null>(null)
  const [selectedAppeal, setSelectedAppeal] = useState<Appeal | null>(null)
  const [outcome, setOutcome] = useState('UPHELD')
  const [resolution, setResolution] = useState('')
  const [target, setTarget] = useState('')
  const [role, setRole] = useState('REVIEWER')
  const [actionReason, setActionReason] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  async function load(next = 0) {
    if (!reason.trim()) return
    setBusy(true); setError(''); setNotice('')
    try {
      const parameters = new URLSearchParams({ reason, offset: String(next) })
      if (view === 'directory') { parameters.set('type', type); parameters.set('query', query); setItems(await request(`/directory?${parameters}`)) }
      if (view === 'appeals') setAppeals(await request(`/appeals?${parameters}`))
      if (view === 'operators') setOperators(await request(`/operators?${parameters}`))
      setOffset(next)
    } catch { setError('Records could not be loaded. Check your investigation reason and verification, then try again.') }
    finally { setBusy(false) }
  }

  async function resolve(event: FormEvent) {
    event.preventDefault()
    if (!selectedAppeal || confirmation !== selectedAppeal.restrictionId) return
    setBusy(true); setError(''); setNotice('')
    try {
      await request(`/appeals/${selectedAppeal.id}/resolution`, { method: 'POST', body: JSON.stringify({ status: outcome, reason: resolution, confirmation }) })
      setAppeals(current => current?.map(item => item.id === selectedAppeal.id ? { ...item, status: outcome, resolution } : item) ?? null)
      setSelectedAppeal(null); setConfirmation(''); setResolution(''); setNotice('Appeal resolution saved. The account receives the decision by email.')
    } catch { setError('The resolution was not confirmed. Reload the appeal before retrying.') }
    finally { setBusy(false) }
  }

  async function changeRole(event: FormEvent) {
    event.preventDefault()
    if (confirmation !== target) return
    setBusy(true); setError(''); setNotice('')
    try {
      await request(`/operators/${target}`, { method: 'PUT', body: JSON.stringify({ role: role === 'REVOKED' ? null : role, reason: actionReason, confirmation }) })
      setConfirmation(''); setOperators(null); setNotice('Operator role saved. Load the operator list to review current grants.')
    } catch { setError('The role change was not confirmed. Reload current grants before retrying. Another administrator must change your own role.') }
    finally { setBusy(false) }
  }

  return <section className="operator-admin" aria-label="Administrator tools">
    <h2>Administrator tools</h2>
    <nav aria-label="Administrator views">{(['directory', 'appeals', 'operators'] as const).map(value => <button key={value} aria-pressed={view === value} onClick={() => { setView(value); setOffset(0); setItems(null); setAppeals(null); setOperators(null); setSelectedAppeal(null); setConfirmation(''); setError(''); setNotice('') }}>{value === 'directory' ? 'Find accounts and servers' : value === 'appeals' ? 'Review appeals' : 'Operator roles'}</button>)}</nav>
    <p>Each read uses the investigation reason above and is recorded in the audit trail.</p>
    {error && <p role="alert" className="inline-error">{error}</p>}{notice && <p role="status" className="moderation-notice">{notice}</p>}
    <form className="moderation-form" onSubmit={event => { event.preventDefault(); void load() }}>
      {view === 'directory' && <><label>Find<select value={type} onChange={event => setType(event.target.value)}><option value="USER">Accounts</option><option value="STUDY_SERVER">Study Servers</option></select></label><label>Name, exact email or reference<input required minLength={3} maxLength={120} value={query} onChange={event => setQuery(event.target.value)} /></label></>}
      <button type="submit" disabled={busy || !reason.trim()}>{busy ? 'Loading…' : view === 'directory' ? 'Search directory' : view === 'appeals' ? 'Load appeals' : 'Load operators'}</button>
    </form>
    {view === 'directory' && items && <>{!items.length ? <p>No matching accounts or Study Servers.</p> : <ul className="moderation-list">{items.map(item => <li key={item.id}><strong>{item.name}</strong><p className="operator-reference">{item.id}</p><button onClick={() => openTarget(item.id)}>View reports for this {item.type === 'USER' ? 'account' : 'Study Server'}</button></li>)}</ul>}</>}
    {view === 'appeals' && appeals && <>{!appeals.length ? <p>No appeals in this page.</p> : <ul className="moderation-list">{appeals.map(item => <li key={item.id}><strong>{item.status.toLowerCase()}</strong><p>{item.body}</p><p className="operator-reference">Restriction {item.restrictionId}</p>{item.resolution && <p>{item.resolution}</p>}{item.status === 'PENDING' && <button onClick={() => { setSelectedAppeal(item); setConfirmation(''); setResolution('') }}>Review appeal</button>}</li>)}</ul>}</>}
    {view !== 'operators' && (items !== null || appeals !== null) && <nav aria-label="Administrator result pages"><button disabled={busy || offset === 0} onClick={() => void load(Math.max(0, offset - 50))}>Previous page</button><span>Page {offset / 50 + 1}</span><button disabled={busy || offset >= 10000 || (view === 'directory' ? items?.length : appeals?.length) !== 50} onClick={() => void load(offset + 50)}>Next page</button></nav>}
    {view === 'appeals' && selectedAppeal && <form className="moderation-form" onSubmit={resolve}><h3>Resolve appeal</h3><p>{selectedAppeal.body}</p><label>Decision<select value={outcome} onChange={event => setOutcome(event.target.value)}><option value="UPHELD">Keep the restriction</option><option value="REVERSED">Lift the restriction</option></select></label><label>Decision reason sent to the account<textarea required maxLength={2000} value={resolution} onChange={event => setResolution(event.target.value)} /></label><p>This resolves this appeal. Lifting this restriction does not remove other restrictions.</p><label>Confirm restriction reference <code>{selectedAppeal.restrictionId}</code><input required value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label><button type="submit" disabled={busy || confirmation !== selectedAppeal.restrictionId || !resolution.trim()}>Save appeal decision</button></form>}
    {view === 'operators' && <>{operators && <ul className="moderation-list">{operators.map(item => <li key={item.userId}><p className="operator-reference">{item.userId}</p><p>{item.revoked ? 'Revoked' : item.role === 'ADMIN' ? 'Administrator' : 'Reviewer'} · {item.factorEnrolled ? 'Authenticator enrolled' : 'Authenticator not enrolled'}</p></li>)}</ul>}<form className="moderation-form" onSubmit={changeRole}><h3>Change an operator role</h3><p>The account must have a verified email and password. Reviewers can access only assigned cases; administrators can change grants and account restrictions.</p><label>Account reference<input required value={target} onChange={event => { setTarget(event.target.value); setConfirmation('') }} /></label><label>Platform role<select value={role} onChange={event => { setRole(event.target.value); setConfirmation('') }}><option value="REVIEWER">Reviewer</option><option value="ADMIN">Administrator</option><option value="REVOKED">Revoke operator access</option></select></label><label>Role change reason<textarea required maxLength={2000} value={actionReason} onChange={event => setActionReason(event.target.value)} /></label><label>Confirm account reference <code>{target || 'Enter the target account first'}</code><input required value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label><button type="submit" disabled={busy || !target || confirmation !== target || !actionReason.trim()}>Save operator role</button></form></>}
  </section>
}
