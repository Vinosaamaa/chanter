import { useCallback, useState } from 'react'
import { CalendarDays, ChevronRight, CircleHelp, ClipboardCheck, Clock, FileText, Sparkles, UsersRound } from 'lucide-react'
import { Navigate, useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../../stores/auth-store'
import { useInstructorDashboardPage } from '../../instructor-dashboard/hooks/use-instructor-dashboard-page'
import { useV2SidebarData } from '../hooks/use-v2-sidebar-data'

export function TeachingPage() {
  const access = useV2SidebarData()
  if (access.isLoading) return <section className="v2-workspace-page course-workspace-state" role="status"><p>Loading teaching…</p></section>
  if (!access.showTeachingNav) return <Navigate to="/app/home" replace />
  return <TeachingContent />
}

function TeachingContent() {
  const [selectedServerId,setSelectedServerId]=useState<string|null>(null)
  const selectServer=useCallback((id:string)=>setSelectedServerId(id),[])
  const page=useInstructorDashboardPage(selectedServerId,selectServer)
  const user=useAuthStore((state)=>state.user)
  const navigate=useNavigate()
  const dashboard=page.dashboard
  const used=dashboard?.aiInvocationCount ?? 780
  const limit=dashboard?.aiInvocationLimit || 1000
  const percent=Math.min(100,Math.round((used/limit)*100))
  const firstServer=page.selectedServerId ?? 'server-demo'
  const goQuestions=()=>navigate(`/app/servers/${firstServer}/courses/course-demo/questions`)

  return <section className="teaching-page"><header><h1>Good evening, {user?.displayName ?? 'Dr. Alex Johnson'}</h1><p>Teaching overview</p><span>Cross-course actions for courses you instruct</span></header>{page.error?<p className="inline-error">{page.error}</p>:null}<div className="teaching-metrics"><article><span className="purple"><CircleHelp /></span><div><strong>{dashboard?.unansweredSupportQuestions ?? 8}</strong><p>Open questions<br />across 2 courses</p></div><button type="button" onClick={goQuestions}>View</button></article><article><span className="green"><ClipboardCheck /></span><div><strong>{dashboard?.repeatedQuestionGroups ?? 3}</strong><p>FAQ candidates<br />awaiting approval</p></div><button type="button" onClick={goQuestions}>Review</button></article><article><span className="blue"><CalendarDays /></span><div><p>Office hours today</p><strong>2:00 PM</strong><small>CS 101</small></div><button type="button">Join</button></article></div><div className="teaching-grid"><article><h2>Courses you’re teaching</h2>{[['#526fff','CS 101','Spring 2026','5 open questions · 2 FAQ · OH 2PM'],['#3ba55b','MATH 201','Fall 2025','3 open questions · TA queue 2']].map(([color,title,cohort,detail])=><button type="button" key={title} onClick={goQuestions}><i style={{background:color}} /><span><strong>{title} <b>{cohort}</b></strong><small>{detail}</small></span><ChevronRight /></button>)}</article><article><h2>TA queue summary</h2>{[['#526fff','CS 101','2 waiting','longest 1h'],['#3ba55b','MATH 201','1 waiting','45m']].map(([color,title,waiting,longest])=><p key={title}><i style={{background:color}} /><strong>{title}</strong><span>{waiting}</span><small>{longest}</small></p>)}<button type="button" onClick={goQuestions}>View all queues</button></article></div><article className="teaching-ai-usage"><span><Sparkles /></span><div><h2>AI Study Assistant usage</h2><p><b>Pro plan</b>{used} / {limit} AI queries</p><small>Resets Jun 1</small></div><div className="teaching-usage-bar"><i style={{width:`${percent}%`}} /></div><strong>{percent}%</strong></article><nav className="teaching-shortcuts">{[[CircleHelp,'Questions'],[FileText,'Resources'],[Clock,'Office Hours'],[UsersRound,'People']].map(([Icon,label])=><button type="button" key={label as string} onClick={goQuestions}><span><Icon /></span><b>{label as string}</b><small>Per course</small><ChevronRight /></button>)}</nav></section>
}
