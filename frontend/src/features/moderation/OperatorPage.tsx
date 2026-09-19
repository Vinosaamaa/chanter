import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch, ApiError, type ApiFetchInit } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { V2Brand } from '../v2-shell/components/V2Brand'
import { sourceNames, type Report, type SourceType } from './moderation-api'
import { OperatorCase, type CaseDetail, type PrivilegedRequest } from './OperatorCase'
import { OperatorAdminTools } from './OperatorAdminTools'
import './moderation.css'

type Operator = { userId: string; role: 'ADMIN' | 'REVIEWER'; enrolled: boolean }
type Verification = { token: string; expiresAt: string }
type QueueItem = { report: Report; assignedTo: string | null }

export function OperatorPage() {
  const user = useAuthStore(state => state.user?.id)
  return <OperatorWorkspace key={user ?? 'signed-out'} />
}

function OperatorWorkspace() {
  const [operator, setOperator] = useState<Operator | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [enrollment, setEnrollment] = useState<{ secret: string; authenticatorUri: string } | null>(null)
  const [verified, setVerified] = useState<Verification | null>(null)
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState('')
  const [status, setStatus] = useState('')
  const [queue, setQueue] = useState<QueueItem[] | null>(null)
  const [detail, setDetail] = useState<CaseDetail | null>(null)
  const [query, setQuery] = useState('')
  const [targetId, setTargetId] = useState('')
  const [offset, setOffset] = useState(0)
  const activeVerification = useRef<Verification | null>(null)

  function lock() { activeVerification.current = null; setVerified(null); setQueue(null); setDetail(null) }

  useEffect(() => {
    const abort = new AbortController()
    void apiFetch<Operator>('/api/v1/platform-admin/verification', { signal: abort.signal, skipAuthRefresh: true })
      .then(setOperator).catch(() => { if (!abort.signal.aborted) setError('Operator access could not be verified. Sign in with an authorized operator account and try again.') })
      .finally(() => { if (!abort.signal.aborted) setLoading(false) })
    return () => abort.abort()
  }, [])
  useEffect(() => {
    if (!verified) return
    const timer = window.setTimeout(() => {
      lock(); setError('Verification expired. Verify again to continue.')
    }, Math.max(0, Date.parse(verified.expiresAt) - Date.now()))
    return () => window.clearTimeout(timer)
  }, [verified])

  async function verify(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (!operator?.enrolled && !enrollment) {
        setEnrollment(await apiFetch('/api/v1/platform-admin/verification/enrollment', { method: 'POST', body: JSON.stringify({ password }), skipAuthRefresh: true }))
      } else {
        const proof = await apiFetch<Verification>(`/api/v1/platform-admin/verification/${enrollment ? 'confirmation' : 'challenge'}`, {
          method: 'POST', body: JSON.stringify({ password, code }), skipAuthRefresh: true,
        })
        activeVerification.current = proof; setVerified(proof); setEnrollment(null); setPassword(''); setCode('')
        setOperator(current => current && { ...current, enrolled: true })
      }
    } catch { setError('Operator verification failed. Check your password and current authenticator code, or ask the operator responsible for enrollment.') }
    finally { setBusy(false) }
  }

  const privileged: PrivilegedRequest = async <T,>(path: string, init?: ApiFetchInit): Promise<T> => {
    const proof = activeVerification.current
    if (!proof) throw new Error('Verification required')
    try {
      const result = await apiFetch<T>(`/api/v1/platform-admin${path}`, { ...init, skipAuthRefresh: true, headers: { 'X-Chanter-Operator-Verification': proof.token } })
      if (activeVerification.current !== proof) throw new Error('Verification ended during request')
      return result
    } catch (failure) {
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) {
        lock()
      }
      throw failure
    }
  }

  async function loadReports(next = 0, target = targetId) {
    setBusy(true); setError(''); setDetail(null)
    try { setQueue(await privileged<QueueItem[]>(`/reports?${new URLSearchParams({ reason, query, offset: String(next), ...(target ? { targetId: target } : {}), ...(status ? { status } : {}) })}`)); setOffset(next) }
    catch { setError('The report queue could not be loaded. Check your verification and try again.') }
    finally { setBusy(false) }
  }
  async function openReport(id: string) {
    setBusy(true); setError(''); setDetail(null)
    try { setDetail(await privileged<CaseDetail>(`/reports/${id}?${new URLSearchParams({ reason })}`)) }
    catch { setError('This report could not be opened. It may be outside your assignment, or access may have changed.') }
    finally { setBusy(false) }
  }

  return <main className="moderation-page operator-page">
    <header className="operator-header"><V2Brand to="/app/home" /><div><h1>Moderation</h1><p>Restricted operator workspace</p></div><Link to="/app/home">Return to Chanter</Link></header>
    {error && <p role="alert" className="inline-error">{error}</p>}
    {loading && <p role="status">Checking operator access…</p>}
    {operator && !verified && <form className="moderation-form" onSubmit={verify}>
      <h2>{operator.enrolled ? 'Verify operator access' : 'Set up operator verification'}</h2>
      <p>Use your account password and authenticator. Verification lasts five minutes and ends when this browser session is revoked.</p>
      <label>Account password<input type="password" required autoComplete="current-password" maxLength={128} value={password} onChange={event => setPassword(event.target.value)} /></label>
      {enrollment && <div className="moderation-notice"><p>Add this secret to your authenticator, then enter its current code.</p><code>{enrollment.secret}</code><p><a href={enrollment.authenticatorUri}>Open authenticator</a></p></div>}
      {(operator.enrolled || enrollment) && <label>Authenticator code<input inputMode="numeric" autoComplete="one-time-code" required pattern="[0-9]{6}" maxLength={6} value={code} onChange={event => setCode(event.target.value)} /></label>}
      <button type="submit" disabled={busy}>{busy ? 'Verifying…' : operator.enrolled || enrollment ? 'Verify operator access' : 'Set up authenticator'}</button>
    </form>}
    {operator && verified && <>
      <form className="moderation-form operator-filters" onSubmit={event => { event.preventDefault(); void loadReports() }}>
        <label>Investigation reason<input required maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>
        <label>Report status<select value={status} onChange={event => setStatus(event.target.value)}><option value="">All reports</option>{['NEW', 'ASSIGNED', 'ESCALATED', 'RESOLVED'].map(value => <option key={value} value={value}>{value.toLowerCase()}</option>)}</select></label>
        <label>Report reason or reference<input maxLength={120} value={query} onChange={event => setQuery(event.target.value)} /></label>
        <label>Target reference filter<input value={targetId} onChange={event => setTargetId(event.target.value)} /></label>
        <button type="submit" disabled={busy || !reason.trim()}>Load reports</button>
        <button type="button" onClick={lock}>Lock workspace</button>
      </form>
      {busy && <p role="status">Loading operator records…</p>}
      <div className={`operator-cases${detail ? ' case-open' : ''}`}>
        <aside aria-label="Report queue"><h2>Reports</h2>{queue === null ? <p>Enter an investigation reason to read the queue. Every evidence read is recorded.</p> : !queue.length ? <p>No reports match this view.</p> : <ul className="moderation-list">{queue.map(item => <li key={item.report.id}><button disabled={busy} aria-current={detail?.report.id === item.report.id ? 'true' : undefined} onClick={() => void openReport(item.report.id)}><strong>{sourceNames[item.report.targetType as SourceType]}</strong><span>{item.report.status.toLowerCase()}</span><p>{item.report.reason}</p><time dateTime={item.report.createdAt}>{new Date(item.report.createdAt).toLocaleString()}</time></button></li>)}</ul>}</aside>
        {detail && <OperatorCase key={detail.report.id} detail={detail} operator={operator} reason={reason} request={privileged} reload={async () => { setDetail(await privileged<CaseDetail>(`/reports/${detail.report.id}?${new URLSearchParams({ reason })}`)) }} close={() => setDetail(null)} />}
      </div>
      {queue && <nav className="operator-paging" aria-label="Report pages"><button disabled={busy || offset === 0} onClick={() => void loadReports(Math.max(0, offset - 50))}>Previous reports</button><span>Page {offset / 50 + 1}</span><button disabled={busy || queue.length !== 50 || offset >= 10000} onClick={() => void loadReports(offset + 50)}>Next reports</button></nav>}
      {operator.role === 'ADMIN' && <OperatorAdminTools key={verified.token} request={privileged} reason={reason} openTarget={id => { setTargetId(id); void loadReports(0, id) }} />}
    </>}
  </main>
}
