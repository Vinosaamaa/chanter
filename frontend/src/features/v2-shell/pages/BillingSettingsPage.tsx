import { useCallback, useState } from 'react'
import {
  CreditCard,
  Info,
  Plug,
  Settings,
  Sparkles,
  Sprout,
  UserCircle,
  UsersRound,
  X,
} from 'lucide-react'
import { Link, Navigate, useNavigate } from 'react-router-dom'

import { useInstructorDashboardPage } from '../../instructor-dashboard/hooks/use-instructor-dashboard-page'
import {
  SAAS_PLAN_LABELS,
  SAAS_PLAN_TIERS,
  type SaasPlanTier,
} from '../../instructor-dashboard/instructor-dashboard-types'
import { useAccessibleStudyServersQuery } from '../../shell/hooks/use-shell-queries'
import { useV2SidebarData } from '../hooks/use-v2-sidebar-data'
import { HomePage } from './HomePage'

const PLAN_LIMIT_COPY: Record<SaasPlanTier, string> = {
  STARTER: '5 AI queries / month',
  PRO: '100 AI queries / month',
  ORGANIZATION: '1,000 AI queries / month',
}

export function BillingSettingsPage() {
  const sidebar = useV2SidebarData()
  const servers = useAccessibleStudyServersQuery()

  if (servers.isLoading || sidebar.isLoading) {
    return (
      <section className="v2-workspace-page course-workspace-state" role="status">
        <p>Loading billing…</p>
      </section>
    )
  }

  if (!sidebar.showBillingNav) {
    return <Navigate to="/app/home" replace />
  }

  const ownerServer = servers.data?.find((server) => server.owner)

  if (!ownerServer) {
    return <Navigate to="/app/home" replace />
  }

  return <OwnerBillingSettingsPage initialServerId={ownerServer.id} />
}

function OwnerBillingSettingsPage({ initialServerId }: { initialServerId: string }) {
  const [selectedServerId, setSelectedServerId] = useState<string | null>(initialServerId)
  const selectServer = useCallback((id: string) => setSelectedServerId(id), [])
  const page = useInstructorDashboardPage(selectedServerId, selectServer)
  const navigate = useNavigate()

  const serverName =
    page.servers.find((server) => server.id === page.selectedServerId)?.name
    ?? 'Study Server'
  const dashboard = page.dashboard
  const used = dashboard?.aiInvocationCount ?? 0
  const limit = dashboard?.aiInvocationLimit ?? 0
  const remaining = dashboard?.remainingAiInvocations ?? Math.max(0, limit - used)
  const aiPercent = limit > 0 ? Math.min(100, Math.round((used / limit) * 100)) : 0
  const planTier = (dashboard?.planTier as SaasPlanTier | undefined) ?? page.planTierDraft
  const planLabel = SAAS_PLAN_LABELS[planTier] ?? planTier
  const quotaExhausted = Boolean(dashboard?.quotaExhausted) || (limit > 0 && remaining <= 0)
  const nearExhausted = !quotaExhausted && limit > 0 && aiPercent >= 80

  return (
    <>
      <HomePage />
      <div className="settings-overlay">
        <section className="settings-modal" aria-label="Plan and Billing settings">
          <aside>
            <h2>Settings</h2>
            <small>USER ACCOUNT</small>
            <Link to="/app/home">
              <UserCircle />
              My account
            </Link>
            <small>STUDY SERVER</small>
            <h3>
              <Sprout />
              {serverName}
            </h3>
            {page.servers.length > 1 ? (
              <label className="billing-server-select">
                <span>Study Server</span>
                <select
                  value={page.selectedServerId ?? ''}
                  onChange={(event) => page.setSelectedServerId(event.target.value)}
                  aria-label="Select Study Server"
                >
                  {page.servers.map((server) => (
                    <option value={server.id} key={server.id}>
                      {server.name}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            <button type="button" disabled title="General settings are not available yet">
              <Settings />
              General
            </button>
            <button type="button" disabled title="Members and roles settings are not available yet">
              <UsersRound />
              Members and roles
            </button>
            <button type="button" className="active">
              <CreditCard />
              Plan and Billing
            </button>
            <button type="button" disabled title="Integrations are not available yet">
              <Plug />
              Integrations
            </button>
          </aside>
          <main>
            <button
              type="button"
              className="settings-close"
              aria-label="Close settings"
              onClick={() => navigate(-1)}
            >
              <X />
            </button>
            <header>
              <h1>Plan and Billing</h1>
              <p>Manage plan and AI usage for {serverName}</p>
            </header>

            {page.isLoading ? <p role="status">Loading plan usage…</p> : null}
            {page.error ? <p className="inline-error">{page.error}</p> : null}
            {page.actionMessage ? (
              <p
                className={
                  page.actionMessage.toLowerCase().includes('unable')
                    || page.actionMessage.toLowerCase().includes('failed')
                    ? 'inline-error'
                    : 'inline-success'
                }
              >
                {page.actionMessage}
              </p>
            ) : null}

            {quotaExhausted ? (
              <div className="quota-warning" role="status">
                <Info />
                <span>
                  <strong>AI query quota exhausted</strong>
                  <small>
                    You’ve used all {limit} AI queries on the {planLabel} plan. Change plan to continue.
                  </small>
                </span>
              </div>
            ) : null}
            {nearExhausted ? (
              <div className="quota-warning" role="status">
                <Info />
                <span>
                  <strong>AI query quota nearly exhausted</strong>
                  <small>
                    You’ve used {aiPercent}% of your monthly AI queries ({used} of {limit}).
                  </small>
                </span>
              </div>
            ) : null}

            <div className="billing-summary">
              <article className="current-plan">
                <h2>Current plan</h2>
                <div>
                  <span>
                    <Sparkles />
                  </span>
                  <strong>{planLabel}</strong>
                  <small>Local SaaS plan for this Study Server (no payment provider).</small>
                </div>
                <p>
                  <b>{PLAN_LIMIT_COPY[planTier]}</b>
                </p>
                <small>Changing plan updates local quotas only — it does not charge a card.</small>
                <ul>
                  <li>{PLAN_LIMIT_COPY[planTier]}</li>
                  <li>Owner-managed Study Server quotas</li>
                  <li>Teaching dashboard metrics</li>
                </ul>
                <label>
                  Change plan
                  <select
                    aria-label="Change plan"
                    value={page.planTierDraft}
                    onChange={(event) =>
                      page.setPlanTierDraft(event.target.value as SaasPlanTier)
                    }
                    disabled={page.isUpdatingPlan}
                  >
                    {SAAS_PLAN_TIERS.map((tier) => (
                      <option value={tier} key={tier}>
                        {SAAS_PLAN_LABELS[tier]}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  className="v2-primary-button"
                  onClick={() => void page.savePlan()}
                  disabled={page.isUpdatingPlan || page.planTierDraft === planTier}
                >
                  {page.isUpdatingPlan ? 'Saving…' : 'Save plan change'}
                </button>
              </article>

              <article className="billing-usage">
                <h2>
                  Usage
                  <small>AI invocations for the current plan period</small>
                </h2>
                <UsageBar
                  label="AI Queries"
                  value={
                    limit > 0
                      ? `${used} of ${limit} queries used · ${remaining} remaining`
                      : 'Usage unavailable'
                  }
                  percent={aiPercent}
                />
                <p className="billing-usage-note">
                  Storage metering and invoices are not available in this local SaaS model.
                </p>
              </article>
            </div>
          </main>
        </section>
      </div>
    </>
  )
}

function UsageBar({
  label,
  value,
  percent,
}: {
  label: string
  value: string
  percent: number
}) {
  return (
    <div className="billing-usage-row">
      <p>
        <strong>
          {label} <Info />
        </strong>
        <span>{value}</span>
      </p>
      <div>
        <i style={{ width: `${percent}%` }} />
      </div>
      <small>{percent}%</small>
    </div>
  )
}
