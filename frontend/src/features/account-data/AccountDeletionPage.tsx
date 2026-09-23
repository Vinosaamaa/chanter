import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { useSignOut } from '../auth/hooks/use-sign-out'
import { endBrowserSessionLocally } from '../auth/browser-session'
import { cancelDeletion, confirmDeletion, getDeletion, getDeletionReceipt, prepareDeletion, validDeletionId, type DeletionJob } from './account-deletion-api'
import './account-deletion.css'

// Navigation state can survive reload; session-closing handoffs cannot.
const receiptHandoff = crypto.randomUUID()

const states: Record<DeletionJob['state'], [string, string]> = {
  PREPARING: ['Checking ownership', 'No deletion has been confirmed. Refresh status to check whether preparation is ready.'],
  BLOCKED_OWNERSHIP: ['Study Server ownership needs attention', 'Transfer or delete the Study Servers you own, then cancel this preparation before starting another. Ownership is not transferred automatically.'],
  PREPARED: ['Ready to confirm', 'Your account has not been deleted. Confirmation closes account access and cannot be undone.'],
  PREPARATION_EXPIRED: ['Preparation expired', 'Cancel this preparation and wait for its release before preparing a new request.'],
  CANCELLING: ['Cancellation in progress', 'Ownership checks are being released. Wait for cancellation to finish before starting another request.'],
  CANCELLED: ['Preparation cancelled', 'This preparation will not delete your account.'],
  ERASING: ['Cleanup in progress', 'Account access is closed. The services are still processing the deletion request.'],
  WAITING_FOR_REPLICA: ['Recovery acknowledgement pending', 'The services have reported their results. Durable recovery records still need to acknowledge this deletion.'],
  COMPLETE: ['Deletion completed', 'All required service results and recovery acknowledgements are recorded. Some recovery, accounting, shared-course and moderation records may remain; completion does not mean every record was erased.'],
}
const names: Record<string, string> = { auth: 'Account', community: 'Study Servers and memberships', message: 'Messages and questions', media: 'Course files', agent: 'Assistant history', search: 'Search', notification: 'Notifications' }
const irreversible = (state: DeletionJob['state']) => ['ERASING', 'WAITING_FOR_REPLICA', 'COMPLETE'].includes(state)
const cancellable = (state: DeletionJob['state']) => ['PREPARING', 'BLOCKED_OWNERSHIP', 'PREPARED', 'PREPARATION_EXPIRED'].includes(state)
const preparationFailure = 'We could not verify preparation. Refresh status or retry preparation with this same request.'
function failureMessage(failure: unknown, fallback: string) {
  if (failure instanceof ApiError && (failure.status === 428 || failure.status === 401)) return { error: 'Sign in again to continue. A login from the last five minutes is required; refreshing a session does not count.', recentLogin: true }
  if (failure instanceof ApiError && failure.status === 409) return { error: 'This request has changed. Refresh its status before continuing.', recentLogin: false }
  if (failure instanceof ApiError && failure.status === 429) return { error: 'The request limit has been reached. Try again later with this same request.', recentLogin: false }
  return { error: fallback, recentLogin: false }
}

export function AccountDeletionPage() {
  const account = useAuthStore(state => state.user?.id)
  const generation = useAuthStore(state => state.generation)
  const [params] = useSearchParams()
  return account ? <DeletionRequest key={`${account}:${generation}:${params.get('job')}`} account={account} generation={generation} /> : null
}

function DeletionRequest({ account, generation }: { account: string; generation: number }) {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const client = useQueryClient()
  const id = params.get('job')
  const valid = id !== null && validDeletionId(id)
  const signOut = useSignOut(`/app/account-data/delete${valid ? `?job=${id}` : ''}`)
  const queryKey = ['account-deletion', account, id]
  const query = useQuery({ queryKey, queryFn: ({ signal }) => getDeletion(id!, signal), enabled: valid, retry: false, refetchOnWindowFocus: false })
  const job = query.data?.id === id ? query.data : undefined
  const requestId = useRef<string | null>(valid ? id : null)
  const active = useRef(true)
  const inFlight = useRef(false)
  const abort = useRef<AbortController | null>(null)
  const [busy, setBusy] = useState(false)
  const initialFailure = location.state?.deletionPreparationFailure
  const [error, setError] = useState<string>(initialFailure?.jobId === id ? initialFailure.error : '')
  const [recentLogin, setRecentLogin] = useState<boolean>(initialFailure?.jobId === id ? initialFailure.recentLogin : false)
  const [phrase, setPhrase] = useState('')
  const [now, setNow] = useState(Date.now)
  const expires = job ? Date.parse(job.preparationExpiresAt) : NaN
  const expired = !Number.isFinite(expires) || expires <= now
  useEffect(() => {
    active.current = true
    return () => { active.current = false; abort.current?.abort() }
  }, [])
  useEffect(() => {
    if (!Number.isFinite(expires)) return
    const timer = setTimeout(() => setNow(Date.now()), Math.max(0, Math.min(2_147_483_647, expires - Date.now() + 10)))
    return () => clearTimeout(timer)
  }, [expires])
  const current = () => active.current && useAuthStore.getState().user?.id === account && useAuthStore.getState().generation === generation
  function save(changed: DeletionJob) {
    if (!current()) return
    client.setQueryData(['account-deletion', account, changed.id], changed)
    setParams({ job: changed.id }, { replace: true })
  }
  async function receipt(jobId: string, uncertain = false) {
    if (!current()) return
    // The receipt clears this generation only after its lazy public route has mounted.
    await navigate(`/account-deletion/${jobId}${uncertain ? '?uncertain=1' : ''}`, { replace: true, state: { closeSessionGeneration: generation, receiptHandoff } })
  }
  async function run(operation: (signal: AbortSignal) => Promise<void>, fallback: string) {
    if (inFlight.current) return
    inFlight.current = true; setBusy(true); setError(''); setRecentLogin(false)
    const controller = new AbortController(); abort.current = controller
    try { await operation(controller.signal) }
    catch (failure) {
      if (!current()) return
      const message = failureMessage(failure, fallback)
      setRecentLogin(message.recentLogin); setError(message.error)
    } finally { inFlight.current = false; if (current()) setBusy(false) }
  }
  const prepare = () => void run(async signal => {
    if (job?.state === 'CANCELLED') requestId.current = null
    requestId.current ??= crypto.randomUUID()
    const request = requestId.current
    try { save(await prepareDeletion(request, signal)) }
    catch (failure) {
      if (current()) setParams({ job: request }, { replace: true, state: { deletionPreparationFailure: { jobId: request, ...failureMessage(failure, preparationFailure) } } })
      throw failure
    }
  }, preparationFailure)
  const confirm = () => void run(async signal => {
    if (!job || job.state !== 'PREPARED' || phrase !== 'DELETE MY ACCOUNT' || Date.parse(job.preparationExpiresAt) <= Date.now()) return
    try {
      const changed = await confirmDeletion(job.id, signal)
      if (irreversible(changed.state)) await receipt(job.id)
      else save(changed)
    } catch (failure) {
      if (!(failure instanceof ApiError) || (failure.status >= 200 && failure.status < 300) || failure.status === 401 || failure.status >= 500) await receipt(job.id, true)
      else throw failure
    }
  }, 'Could not verify confirmation. Open the receipt to check this request before taking further action.')
  const cancel = () => void run(async signal => { if (job) save(await cancelDeletion(job.id, signal)) }, 'Could not verify cancellation. Refresh this request before trying again.')
  const refresh = () => void run(async () => { const result = await query.refetch(); if (result.error) throw result.error }, 'Could not refresh this request. Try again.')
  const notFound = query.error instanceof ApiError && query.error.status === 404
  const disabled = busy || query.isFetching

  return <div className="deletion-page">
    <Link to="/app/account-data">Back to account data</Link>
    <h1>Delete your account</h1>
    <p>First check whether your account is ready. Preparing a request does not delete anything.</p>
    <section className="deletion-warning" aria-label="Before you confirm">
      <h2>Before you confirm</h2>
      <p>Confirmation closes access to your account and starts irreversible cleanup. Export anything you want to keep first.</p>
      <p>Some recovery, accounting, shared-course and moderation records may remain. Read the <Link to="/privacy">privacy information</Link> for the data and retention boundaries. This request does not promise an immediate completion time.</p>
    </section>
    {id && !valid ? <p role="alert">Invalid deletion request link. Return to account data to start again.</p> : null}
    {valid && query.isPending ? <p role="status">Loading deletion request…</p> : null}
    {query.isError ? <p role="alert">Could not load the current request. Refresh status to check again.</p> : null}
    {error ? <div role="alert"><p>{error}</p>{recentLogin ? <button type="button" disabled={busy} onClick={() => void signOut()}>Sign out to sign in again</button> : null}</div> : null}
    {job ? <DeletionStatus job={job} /> : null}
    {job?.state === 'BLOCKED_OWNERSHIP' ? <Link to="/app/picker">Manage your Study Servers</Link> : null}
    <div className="deletion-actions">
      {!id || (valid && (notFound || job?.state === 'CANCELLED')) ? <button type="button" disabled={disabled} onClick={prepare}>{job?.state === 'CANCELLED' ? 'Prepare a new request' : id ? 'Retry preparation' : 'Prepare deletion'}</button> : null}
      {valid ? <button type="button" disabled={disabled} onClick={refresh}>Refresh status</button> : null}
      {job && cancellable(job.state) ? <button type="button" disabled={disabled} onClick={cancel}>Cancel preparation</button> : null}
      {valid ? <Link to={`/account-deletion/${id}`}>View receipt</Link> : null}
    </div>
    {job?.state === 'PREPARED' ? <section className="deletion-confirm" aria-label="Irreversible confirmation">
      <p>Preparation expires at <time dateTime={job.preparationExpiresAt}>{new Date(job.preparationExpiresAt).toLocaleString()}</time>.</p>
      {expired ? <p role="status">Preparation has expired. Cancel it before preparing another request.</p> : null}
      <label htmlFor="deletion-confirmation">Type DELETE MY ACCOUNT to confirm</label>
      <input id="deletion-confirmation" autoComplete="off" value={phrase} onChange={event => setPhrase(event.target.value)} disabled={disabled || expired} />
      <button type="button" className="deletion-danger" disabled={disabled || expired || phrase !== 'DELETE MY ACCOUNT'} onClick={confirm}>Permanently delete my account</button>
    </section> : null}
  </div>
}

export function AccountDeletionReceiptPage() {
  const { jobId = '' } = useParams()
  const [params] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const client = useQueryClient()
  const closeGeneration: unknown = location.state?.closeSessionGeneration
  const handoff: unknown = location.state?.receiptHandoff
  useEffect(() => {
    if (typeof closeGeneration !== 'number') return
    if (handoff === receiptHandoff && useAuthStore.getState().generation === closeGeneration) {
      const privateQueries = { predicate: (query: { queryKey: readonly unknown[] }) => query.queryKey[0] !== 'account-deletion-receipt' }
      void client.cancelQueries(privateQueries)
      client.removeQueries(privateQueries)
      try { endBrowserSessionLocally() }
      catch { /* In-memory credentials clear even if site storage is unavailable. */ }
    }
    void navigate(location.pathname + location.search, { replace: true, state: null })
  }, [client, closeGeneration, handoff, location.pathname, location.search, navigate])
  const valid = validDeletionId(jobId)
  const query = useQuery({ queryKey: ['account-deletion-receipt', jobId], queryFn: ({ signal }) => getDeletionReceipt(jobId, signal), enabled: valid, retry: false, refetchOnMount: 'always', refetchOnWindowFocus: false })
  const loading = query.isPending || query.isFetching
  const job = !loading && query.isFetchedAfterMount && !query.isError && query.data?.id === jobId ? query.data : undefined
  return <main className="deletion-page deletion-receipt">
    <Link to="/">Chanter</Link><h1>Deletion status</h1>
    <p>This page can only read the status of this request. Keep its address to return in this browser; its receipt cookie expires after seven days.</p>
    {params.has('uncertain') ? <p role="status">The confirmation response was interrupted. Check this receipt; an interrupted response does not establish whether confirmation succeeded.</p> : null}
    {valid && loading ? <p role="status">Loading receipt…</p> : null}
    {!valid || (!loading && (query.isError || (query.isSuccess && !job))) ? <p role="alert">Receipt unavailable. It may have expired, or this browser may not have its receipt cookie. This does not establish whether deletion completed.</p> : null}
    {job ? <DeletionStatus job={job} /> : null}
    {valid ? <button type="button" disabled={query.isFetching} onClick={() => void query.refetch()}>Refresh status</button> : null}
    {job && !irreversible(job.state) && job.state !== 'CANCELLED' ? <p><Link to={`/app/account-data/delete?job=${jobId}`}>Sign in to manage preparation</Link></p> : null}
  </main>
}

function DeletionStatus({ job }: { job: DeletionJob }) {
  const copy = states[job.state] ?? ['Status unavailable', 'Refresh to check this request.']
  return <section className="deletion-status" aria-label="Current deletion status" aria-live="polite">
    <h2>{copy[0]}</h2><p>{copy[1]}</p>
    {job.preparationError || job.parts.some(part => part.errorCode) ? <p role="alert">A service could not finish. The request needs attention; a delivery failure does not mean deletion completed.</p> : null}
    <details><summary>Service results</summary><ul>{job.parts.map(part => <li key={part.source}><span>{names[part.source] ?? 'Service'}</span><span>{part.errorCode ? 'Delivery needs attention' : part.state === 'PRESERVED' ? 'Some records retained' : part.state === 'COMPLETE' ? 'Confirmed' : 'Pending'}</span></li>)}</ul></details>
  </section>
}
