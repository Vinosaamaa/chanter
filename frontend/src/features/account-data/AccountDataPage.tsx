import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Download, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { useSignOut } from '../auth/hooks/use-sign-out'
import { authorizeExportDownload, cancelExport, createExport, listExports, type ExportJob } from './account-data-api'
import './account-data.css'

const sourceNames: Record<string, string> = {
  auth: 'Account', community: 'Study Servers and memberships', message: 'Messages and questions',
  media: 'Course files', agent: 'Assistant history', notification: 'Notifications', search: 'Search coverage',
}
const partState: Record<string, string> = { PENDING: 'Waiting', READY: 'Prepared', CANCELLED: 'Removed' }
const jobState = { BUILDING: 'Preparing', READY: 'Ready to download', CANCELLED: 'Cancelled', EXPIRED: 'Expired' }
function date(value: string) { return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) }

export function AccountDataPage() {
  const userId = useAuthStore(state => state.user?.id)
  const generation = useAuthStore(state => state.generation)
  return userId ? <AccountData key={`${userId}:${generation}`} account={userId} generation={generation} /> : null
}

function AccountData({ account, generation }: { account: string; generation: number }) {
  const client = useQueryClient()
  const key = ['account-exports', account]
  const query = useQuery({ queryKey: key, queryFn: ({ signal }) => listExports(signal), retry: false, refetchOnWindowFocus: false })
  const signOut = useSignOut('/app/account-data')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [recentLogin, setRecentLogin] = useState(false)
  const inFlight = useRef(false)
  const active = useRef(true)
  const abort = useRef<AbortController | null>(null)
  const requestId = useRef<string | null>(null)
  const download = useRef<HTMLAnchorElement>(null)
  useEffect(() => {
    active.current = true
    return () => { active.current = false; abort.current?.abort() }
  }, [])
  const current = () => active.current && useAuthStore.getState().generation === generation && useAuthStore.getState().user?.id === account
  const jobs = query.data?.filter(job => job.accountId === account) ?? []
  const live = jobs.find(job => job.state === 'READY' || job.state === 'BUILDING')
  const selected = live ?? jobs[0]

  function save(job: ExportJob) {
    if (!current() || job.accountId !== account) return
    client.setQueryData<ExportJob[]>(key, old => [job, ...(old ?? []).filter(item => item.id !== job.id)])
  }
  async function act(operation: (signal: AbortSignal) => Promise<void>) {
    if (inFlight.current) return
    inFlight.current = true; setBusy(true); setError(''); setNotice(''); setRecentLogin(false)
    const controller = new AbortController(); abort.current = controller
    try { await operation(controller.signal) }
    catch (failure) {
      if (!current()) return
      if (failure instanceof ApiError && failure.status === 428) {
        setRecentLogin(true); setError('To request an export, sign in again. A refreshed session does not count as a new login.')
      } else if (failure instanceof ApiError && failure.status === 429) {
        setError('Export capacity or your request limit has been reached. Try again later. You can request up to five exports in a rolling 24 hours.')
      } else if (failure instanceof ApiError && failure.status === 409) {
        setError('This request has changed or another export is active. Refresh its status before trying again.')
      } else if (failure instanceof ApiError && failure.status === 401) {
        setRecentLogin(true); setError('Your session is no longer available. Sign in again to access your account data.')
      } else setError('The request could not be completed. Refresh its status, then try again. An interrupted download needs a new download request.')
    } finally {
      inFlight.current = false
      if (current()) setBusy(false)
    }
  }
  const request = () => void act(async signal => {
    requestId.current ??= crypto.randomUUID()
    const job = await createExport(requestId.current, signal)
    if (!current()) return
    save(job); requestId.current = null; setNotice('Export requested. Refresh status to check its progress.')
  })
  const cancel = (job: ExportJob) => void act(async signal => {
    const changed = await cancelExport(job.id, signal)
    if (!current()) return
    save(changed); setNotice('Export cancelled. Download access is closed; source cleanup may still be pending.')
  })
  const receive = (job: ExportJob) => void act(async signal => {
    const url = await authorizeExportDownload(job.id, signal)
    if (!current() || !download.current) return
    download.current.href = url
    download.current.click()
    setNotice('Download requested. Check your browser’s downloads; retry here if it fails.')
  })
  const refresh = () => void act(async () => {
    const result = await query.refetch()
    if (result.error) throw result.error
    if (current()) setNotice('Export status refreshed.')
  })

  return <div className="settings-page account-data-page">
    <section className="settings-modal" aria-label="Account data settings">
      <aside><h2>Settings</h2><h3>Account data</h3><p>Your information in Chanter</p></aside>
      <div className="settings-content account-data-content">
        <Link to="/app/home" className="settings-home-link"><ArrowLeft size={16} /> Back to Home</Link>
        <header><h1>Account data</h1><p>Request a copy of your information or manage account deletion.</p></header>
        <section className="account-data-section" aria-labelledby="export-heading">
          <h2 id="export-heading">Export your data</h2>
          <p>Your archive includes your profile, memberships, authored content, file metadata and available files, and assistant history. Each section explains what it omits.</p>
          <p>Shared content requires current access. Sources capture their sections separately. Download access expires 24 hours after the request; cleanup may still be pending.</p>
          <div className="account-data-actions">
            <button type="button" className="v2-primary-button" disabled={busy || query.isPending || query.isError || Boolean(live)} onClick={request}>Request export</button>
            <button type="button" className="v2-secondary-button" disabled={busy || query.isFetching} onClick={refresh}><RefreshCw size={16} aria-hidden="true" /> Refresh status</button>
          </div>
          <p className="account-data-note">A request requires a login from the last five minutes. Cancelling a request still counts toward the five-request daily limit.</p>
        </section>
        {query.isPending ? <p role="status">Loading export requests…</p> : null}
        {query.isError ? <p className="inline-error" role="alert">Could not load export requests. Refresh status to try again.</p> : null}
        {error ? <div role="alert" className="account-data-error"><p>{error}</p>{recentLogin ? <button className="v2-secondary-button" type="button" onClick={() => void signOut()}>Sign out to sign in again</button> : null}</div> : null}
        <p className="account-data-notice" role="status" aria-live="polite">{notice}</p>
        {selected ? <section className="account-data-section" aria-labelledby="current-export-heading">
          <h2 id="current-export-heading">Latest request</h2>
          <ExportProgress job={selected} />
          {selected.state === 'READY' || selected.state === 'BUILDING' ? <div className="account-data-actions">
            {selected.state === 'READY' ? <button type="button" className="v2-primary-button" disabled={busy} onClick={() => receive(selected)}><Download size={17} aria-hidden="true" /> Download ZIP</button> : null}
            <button type="button" className="v2-secondary-button" disabled={busy} onClick={() => cancel(selected)}>Cancel export</button>
          </div> : null}
          {selected.state === 'READY' ? <p className="account-data-note">Keep this archive private. Downloads can contain personal messages and course files. If a download fails, use Download ZIP again to request a fresh one.</p> : null}
        </section> : !query.isPending && !query.isError ? <p>No export requests yet.</p> : null}
        {jobs.length > 1 ? <section className="account-data-section" aria-labelledby="previous-exports-heading">
          <h2 id="previous-exports-heading">Previous requests</h2>
          <ul className="account-data-history">{jobs.filter(job => job.id !== selected?.id).map(job => <li key={job.id}>
            <time dateTime={job.requestedAt}>{date(job.requestedAt)}</time><span>{jobState[job.state]}{job.cleanupPending ? '. Source cleanup pending.' : ''}</span>
          </li>)}</ul><p className="account-data-note">Request history is retained for 30 days after expiry.</p>
        </section> : null}
        <section className="account-data-section" aria-labelledby="deletion-heading">
          <h2 id="deletion-heading">Delete your account</h2>
          <p>Check ownership and review what happens before confirming irreversible deletion. Export anything you want to keep first.</p>
          <Link to="/app/account-data/delete">Review account deletion</Link>
        </section>
        <a ref={download} hidden title="Account export download" download="chanter-account.zip" />
      </div>
    </section>
  </div>
}

function ExportProgress({ job }: { job: ExportJob }) {
  const failed = job.parts.some(part => Boolean(part.errorCode))
  const prepared = job.parts.filter(part => part.state === 'READY').length
  return <>
    <div className="account-data-request"><strong>{failed && job.state === 'BUILDING' ? 'Preparation needs attention' : jobState[job.state]}</strong><time dateTime={job.requestedAt}>Requested {date(job.requestedAt)}</time></div>
    {job.state === 'BUILDING' ? <><progress max={job.parts.length} value={prepared} aria-label="Export sources prepared" /><p>{prepared} of {job.parts.length} sources prepared. {failed ? 'A source could not finish. Cancel this export and request another; repeated failures need operator attention.' : 'Refresh status to check again.'}</p></> : null}
    {job.state === 'READY' ? <p>Available until <time dateTime={job.expiresAt}>{date(job.expiresAt)}</time>.</p> : null}
    {job.state === 'CANCELLED' ? <p>{job.cleanupPending ? 'Download access is closed. Some sources have not yet confirmed removal of their export copies.' : 'Sources confirmed removal of this export’s copies.'}</p> : null}
    {job.state === 'EXPIRED' ? <p>This export is no longer available. Request a new copy when you need it.</p> : null}
    <details className="account-data-sources"><summary>Source details</summary><ul>{job.parts.map(part => <li key={part.source}>
      <span>{sourceNames[part.source] ?? 'Export source'}</span><span>{part.errorCode ? 'Delivery failed' : partState[part.state] ?? 'Unavailable'}</span>
    </li>)}</ul></details>
  </>
}
