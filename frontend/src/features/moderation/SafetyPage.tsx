import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { fetchPublicProfiles } from '../friends/friends-api'
import { sourceNames, sourceType, type Report } from './moderation-api'
import './moderation.css'

export function SafetyPage() {
  const [params] = useSearchParams()
  const viewer = useAuthStore(state => state.user?.id)
  const query = useQueryClient()
  const type = params.get('type')
  const id = params.get('id')
  const target = sourceType(type) && id && /^[0-9a-f-]{36}$/i.test(id) ? { type, id } : null
  const [reason, setReason] = useState('')
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const reports = useQuery({ queryKey: ['moderation-reports', viewer], enabled: !!viewer, queryFn: () => apiFetch<Report[]>('/api/v1/moderation/reports') })
  const blocks = useQuery({ queryKey: ['moderation-blocked', viewer], enabled: !!viewer, queryFn: async () => {
    const result = await apiFetch<{ blockedUserIds: string[] }>('/api/v1/user-blocks')
    const profiles = await fetchPublicProfiles(result.blockedUserIds)
    return result.blockedUserIds.map(id => ({ id, name: profiles.profiles.find(profile => profile.userId === id)?.displayName ?? 'Account unavailable' }))
  } })

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!target) return
    setBusy(true); setError('')
    try {
      await apiFetch<Report>('/api/v1/moderation/reports', { method: 'POST', body: JSON.stringify({ targetType: target.type, targetId: target.id, reason }) })
      setSaved(true); setReason('')
      await query.invalidateQueries({ queryKey: ['moderation-reports', viewer] })
    } catch { setError('Your report was not saved. Check your access to the source and try again.') }
    finally { setBusy(false) }
  }

  async function unblock(id: string) {
    setBusy(true); setError('')
    try {
      await apiFetch(`/api/v1/user-blocks/${encodeURIComponent(id)}`, { method: 'DELETE' })
      await Promise.all([query.invalidateQueries({ queryKey: ['moderation-blocked', viewer] }), query.invalidateQueries({ queryKey: ['friends'] })])
    } catch { setError('The account could not be unblocked. Try again.') }
    finally { setBusy(false) }
  }

  return <section className="moderation-page">
    <header><h1>Safety and reports</h1><p>Review your reports and manage blocked accounts.</p></header>
    {error && <p className="inline-error" role="alert">{error}</p>}
    {target && !saved && <form className="moderation-form" onSubmit={submit}>
      <h2>Report {sourceNames[target.type].toLowerCase()}</h2>
      <p>The report includes a reference to the source you selected. Operators can review its preserved evidence.</p>
      <label>Reason for reporting<textarea required maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>
      <button type="submit" disabled={busy || !reason.trim()}>{busy ? 'Sending…' : 'Send report'}</button>
    </form>}
    {saved && <p role="status" className="moderation-notice">Your report was saved for review.</p>}
    <div className="moderation-columns">
      <section><h2>Your reports</h2>
        {reports.isPending ? <p role="status">Loading reports…</p> : reports.isError ? <p role="alert">Reports could not be loaded. <button onClick={() => void reports.refetch()}>Try again</button></p> : !reports.data.length ? <p>Use Report beside a message, account, Course Resource or Study Server to raise a concern.</p> : <ul className="moderation-list">{reports.data.map(report => <li key={report.id}>
          <strong>{sourceNames[report.targetType]}</strong><span>{report.status.toLowerCase()}</span>
          <p>{report.reason}</p>{report.resolution && <p><strong>Review outcome</strong> {report.resolution}</p>}
          <time dateTime={report.createdAt}>{new Date(report.createdAt).toLocaleDateString()}</time>
        </li>)}</ul>}
      </section>
      <section><h2>Blocked accounts</h2><p>Unblocking allows contact again when you are friends. It does not remove a block placed by the other person.</p>
        {blocks.isPending ? <p role="status">Loading blocked accounts…</p> : blocks.isError ? <p role="alert">Blocked accounts could not be loaded. <button onClick={() => void blocks.refetch()}>Try again</button></p> : !blocks.data.length ? <p>No blocked accounts.</p> : <ul className="moderation-list">{blocks.data.map(account => <li key={account.id}><strong>{account.name}</strong><button disabled={busy} onClick={() => void unblock(account.id)}>Unblock</button></li>)}</ul>}
        <Link to="/app/friends">Back to Friends</Link>
      </section>
    </div>
  </section>
}
