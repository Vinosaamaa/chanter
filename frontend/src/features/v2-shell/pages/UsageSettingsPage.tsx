import { useCallback, useState } from 'react'
import { ArrowLeft, Info, Sprout } from 'lucide-react'
import { Link, Navigate } from 'react-router-dom'
import { useInstructorDashboardPage } from '../../instructor-dashboard/hooks/use-instructor-dashboard-page'
import { useAccessibleStudyServersQuery } from '../../shell/hooks/use-shell-queries'
import { useV2SidebarData } from '../hooks/use-v2-sidebar-data'

export function UsageSettingsPage() {
  const sidebar = useV2SidebarData()
  const servers = useAccessibleStudyServersQuery()
  if (servers.isLoading || sidebar.isLoading) {
    return <section className="v2-workspace-page course-workspace-state" role="status"><p>Loading usage…</p></section>
  }
  if (servers.isError || sidebar.isError) {
    return <section className="v2-workspace-page course-workspace-state">
      <h1>Usage</h1>
      <p role="alert">Unable to load Study Server access. Reload the page to try again.</p>
      <Link to="/app/home">Back to Home</Link>
    </section>
  }
  const ownedServers = servers.data?.filter(server => server.owner) ?? []
  if (!sidebar.showBillingNav || !ownedServers.length) return <Navigate to="/app/home" replace />
  return <OwnerUsagePage servers={ownedServers} />
}

function OwnerUsagePage({ servers }: { servers: Array<{ id: string; name: string }> }) {
  const [selection, setSelection] = useState(servers[0].id)
  const selectedId = servers.some(server => server.id === selection) ? selection : servers[0].id
  const selectServer = useCallback((id: string) => setSelection(id), [])
  const page = useInstructorDashboardPage(selectedId, selectServer)
  const serverName = servers.find(server => server.id === selectedId)?.name ?? 'Study Server'
  const dashboard = page.dashboard
  const used = dashboard?.aiInvocationCount ?? 0
  const limit = dashboard?.aiInvocationLimit ?? 0
  const remaining = dashboard?.remainingAiInvocations ?? 0
  const percent = limit > 0 ? Math.min(100, Math.max(0, Math.round(used / limit * 100))) : 0
  const canShowUsage = Boolean(dashboard && !page.isLoading && !page.error && page.isOwner)

  return <div className="settings-page">
    <section className="settings-modal" aria-label="Study Server usage">
      <aside>
        <h2>Settings</h2>
        <h3><Sprout />{serverName}</h3>
        {servers.length > 1 ? <label className="billing-server-select">
          <span>Study Server</span>
          <select value={selectedId} onChange={event => selectServer(event.target.value)} aria-label="Select Study Server">
            {servers.map(server => <option value={server.id} key={server.id}>{server.name}</option>)}
          </select>
        </label> : null}
      </aside>
      <div className="settings-content">
        <Link to="/app/home" className="settings-home-link"><ArrowLeft size={16} /> Back to Home</Link>
        <header><h1>Usage</h1><p>Assistant runs for {serverName}</p></header>
        {page.isLoading ? <p role="status">Loading usage…</p> : null}
        {page.error ? <p className="inline-error" role="alert">{page.error}</p> : null}
        {!page.isLoading && !page.error && !page.isOwner ? <p role="alert">Only the Study Server owner can view these settings.</p> : null}
        {canShowUsage ? <>
          {remaining <= 0 ? <div className="quota-warning" role="status">
            <Info /><span><strong>Assistant run limit reached</strong><small>Course resources and instructor support remain available.</small></span>
          </div> : percent >= 80 ? <div className="quota-warning" role="status">
            <Info /><span><strong>Assistant run limit nearly reached</strong><small>{remaining.toLocaleString()} runs remain for this Study Server.</small></span>
          </div> : null}
          <div className="billing-summary">
            <article className="current-plan">
              <h2>Free beta</h2>
              <p>Chanter is free during beta. Your Study Server has {limit.toLocaleString()} assistant runs, shared by its members.</p>
              <p>The platform sets this limit to keep the beta available.</p>
            </article>
            <article className="billing-usage">
              <h2>Assistant runs <small>Lifetime usage</small></h2>
              <div className="billing-usage-row">
                <p><strong>Runs used</strong><span>{used.toLocaleString()} of {limit.toLocaleString()} assistant runs used, {remaining.toLocaleString()} remaining</span></p>
                <div role="progressbar" aria-label="Assistant runs used" aria-valuemin={0} aria-valuemax={limit} aria-valuenow={Math.min(used, limit)}>
                  <i style={{ width: percent + '%' }} />
                </div>
                <small>{percent}%</small>
              </div>
              <p className="billing-usage-note">This count includes all saved assistant runs for this Study Server. It does not reset monthly.</p>
            </article>
          </div>
        </> : null}
      </div>
    </section>
  </div>
}
