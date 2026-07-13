import { ChevronDown, Radio, UserPlus } from 'lucide-react'
import { NavLink, useLocation } from 'react-router-dom'

import type { V2CourseTab } from '../v2-routes'
import { v2CoursePath } from '../v2-routes'

type CourseChromeContext = {
  serverId: string
  courseId: string
  course: { title: string; cohorts: { name: string }[] }
  isOwner: boolean
}

const tabs: { id: V2CourseTab; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'chat', label: 'Chat' },
  { id: 'questions', label: 'Questions' },
  { id: 'resources', label: 'Resources' },
  { id: 'office-hours', label: 'Office Hours' },
  { id: 'people', label: 'People' },
]

export function V2CourseChrome({ context }: { context: CourseChromeContext }) {
  const location = useLocation()
  const cohortName = context.course.cohorts[0]?.name ?? 'Spring cohort'
  const activeTab = tabs.find((tab) => location.pathname.endsWith(`/${tab.id}`))?.id ?? 'overview'

  return (
    <header className="course-workspace-chrome">
      <div className="course-heading-row">
        <div className="course-heading-copy">
          <span className="course-workspace-dot" />
          <div>
            <h1>{context.course.title}</h1>
            <p>
              {cohortName}
              {context.isOwner ? <ChevronDown size={16} /> : null}
              <i>·</i> Dr. Alex Johnson
            </p>
          </div>
        </div>
        <div className="course-heading-actions">
          <span className="study-room-status"><i /> <Radio size={17} /> Study Room · 2 live</span>
          <button type="button" className="v2-outline-button"><UserPlus size={18} /> Invite</button>
        </div>
      </div>
      <nav className="workspace-tabs" aria-label="Course workspace tabs">
        {tabs.map((tab) => (
          <NavLink key={tab.id} to={v2CoursePath(context.serverId, context.courseId, tab.id)} className={activeTab === tab.id ? 'active' : undefined}>
            {tab.label}{tab.id === 'questions' ? <i /> : null}
          </NavLink>
        ))}
      </nav>
    </header>
  )
}
