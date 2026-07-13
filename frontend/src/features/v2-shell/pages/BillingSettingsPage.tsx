import { useCallback, useState } from 'react'
import { BarChart3, CreditCard, Download, ExternalLink, Info, Plug, Settings, Sparkles, Sprout, UserCircle, UsersRound, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { updateSaasPlan } from '../../instructor-dashboard/instructor-dashboard-api'
import { useInstructorDashboardPage } from '../../instructor-dashboard/hooks/use-instructor-dashboard-page'
import { HomePage } from './HomePage'

export function BillingSettingsPage() {
  const [selectedServerId,setSelectedServerId]=useState<string|null>(null)
  const selectServer=useCallback((id:string)=>setSelectedServerId(id),[])
  const page=useInstructorDashboardPage(selectedServerId,selectServer)
  const navigate=useNavigate()
  const [updating,setUpdating]=useState(false)
  const [message,setMessage]=useState<string|null>(null)
  const used=page.dashboard?.aiInvocationCount ?? 780
  const limit=page.dashboard?.aiInvocationLimit || 1000
  const aiPercent=Math.min(100,Math.round((used/limit)*100))
  const upgrade=async()=>{if(!page.selectedServerId)return;setUpdating(true);setMessage(null);try{await updateSaasPlan(page.selectedServerId,'ORGANIZATION');setMessage('Plan updated to Organization.');await page.refresh()}catch(error){setMessage(error instanceof Error?error.message:'Unable to update plan.')}finally{setUpdating(false)}}
  return <><HomePage /><div className="settings-overlay"><section className="settings-modal"><aside><h2>Settings</h2><small>USER ACCOUNT</small><button type="button"><UserCircle />My account</button><small>STUDY SERVER</small><h3><Sprout />Spring Bootcamp Hub</h3><button type="button"><Settings />General</button><button type="button"><UsersRound />Members and roles</button><button type="button" className="active"><CreditCard />Plan and Billing</button><button type="button"><Plug />Integrations</button><small>COURSE</small><button type="button"><i />CS 101 — Intro to CS</button></aside><main><button type="button" className="settings-close" onClick={()=>navigate(-1)}><X /></button><header><h1>Plan and Billing</h1><p>Manage subscription usage and billing for Spring Bootcamp Hub</p></header><div className="quota-warning"><Info /><span><strong>AI query quota nearly exhausted</strong><small>You’ve used {aiPercent}% of your monthly AI queries. Upgrade your plan or wait for the next reset.</small></span><button type="button">View Usage</button></div>{message?<p className={message.startsWith('Plan updated')?'inline-success':'inline-error'}>{message}</p>:null}<div className="billing-summary"><article className="current-plan"><h2>Current plan</h2><div><span><Sparkles /></span><strong>Pro</strong><small>For growing study communities and advanced learning.</small></div><p><b>$29.00</b> / month</p><small>Billed monthly · Next billing date: Jun 1, 2026</small><ul><li>Up to 1,000 AI queries / month</li><li>50 GB storage</li><li>Advanced analytics</li><li>Priority support</li></ul><button type="button" className="v2-primary-button" onClick={()=>void upgrade()} disabled={updating}>{updating?'Updating…':'Upgrade to Organization'} <ExternalLink /></button><button type="button" className="compare-plans">Compare plans</button></article><article className="billing-usage"><h2>Usage <small>Resets on Jun 1, 2026</small></h2><UsageBar label="AI Queries" value={`${used} of ${limit} queries used`} percent={aiPercent} /><UsageBar label="Storage" value="45 GB of 50 GB used" percent={90} /><button type="button"><BarChart3 />View Usage Details</button></article></div><article className="invoice-history"><h2>Invoice history</h2><div className="invoice-head"><span>Invoice</span><span>Date</span><span>Plan</span><span>Amount</span><span>Status</span><span>Download</span></div>{['0426','0326','0226','0126'].map((id,index)=><div key={id}><span>INV-2026-{id}</span><span>{['Apr 26','Mar 26','Feb 26','Jan 26'][index]}, 2026</span><span>Pro (Monthly)</span><span>$29.00</span><b>Paid</b><button type="button"><Download /></button></div>)}<button type="button">View all invoices</button></article></main></section></div></>
}
function UsageBar({label,value,percent}:{label:string;value:string;percent:number}){return <div className="billing-usage-row"><p><strong>{label} <Info /></strong><span>{value}</span></p><div><i style={{width:`${percent}%`}} /></div><small>{percent}%</small></div>}
