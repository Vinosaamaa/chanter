import { ChevronDown, UserPlus } from 'lucide-react'
import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'

import { useCohortRosterQuery } from '../../people/use-cohort-roster'
import type { ShellCohort } from '../../shell/types'
import type { V2CourseTab } from '../v2-routes'
import { v2CoursePath } from '../v2-routes'

type CourseChromeContext = {
  serverId: string
  courseId: string
  course: { title: string; cohorts: ShellCohort[] }
  selectedCohort: ShellCohort | undefined
  selectCohort: (cohortId: string) => void
  courseCapabilities: { canManagePeople: boolean }
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
  const roster = useCohortRosterQuery(context.selectedCohort?.id)
  const [cohortMenuOpen, setCohortMenuOpen] = useState(false)
  const cohortName = context.selectedCohort?.name ?? 'Cohort'
  const activeTab = tabs.find((tab) => location.pathname.endsWith(`/${tab.id}`))?.id ?? 'overview'
  const showInviteAction = activeTab !== 'people' && context.courseCapabilities.canManagePeople
  const canSwitchCohort = context.course.cohorts.length > 1
  const instructorName = roster.data?.instructor.displayName ?? 'Course instructor'

  return (
    <header className="course-workspace-chrome">
      <div className="course-heading-row">
        <div className="course-heading-copy">
          <span className="course-workspace-dot" />
          <div>
            <h1>{context.course.title}</h1>
            <p>
              {canSwitchCohort ? (
                <span className="course-cohort-picker">
                  <button
                    type="button"
                    aria-label="Change cohort"
                    aria-expanded={cohortMenuOpen}
                    onClick={() => setCohortMenuOpen((open) => !open)}
                  >
                    {cohortName}<ChevronDown size={16} />
                  </button>
                  {cohortMenuOpen ? (
                    <span className="course-cohort-menu" role="menu" aria-label="Available cohorts">
                      {context.course.cohorts.map((cohort) => (
                        <button
                          type="button"
                          role="menuitemradio"
                          aria-checked={cohort.id === context.selectedCohort?.id}
                          key={cohort.id}
                          onClick={() => {
                            context.selectCohort(cohort.id)
                            setCohortMenuOpen(false)
                          }}
                        >
                          {cohort.name}
                        </button>
                      ))}
                    </span>
                  ) : null}
                </span>
              ) : cohortName}
              <i>·</i> {instructorName}
            </p>
          </div>
        </div>
        {showInviteAction ? <div className="course-heading-actions">
          <NavLink
            className="v2-outline-button"
            to={`${v2CoursePath(context.serverId, context.courseId, 'people')}${context.selectedCohort ? `?cohort=${encodeURIComponent(context.selectedCohort.id)}` : ''}`}
          >
            <UserPlus size={18} /> Invite
          </NavLink>
        </div> : null}
      </div>
      <nav className="workspace-tabs" aria-label="Course workspace tabs">
        {tabs.map((tab) => (
          <NavLink
            key={tab.id}
            to={`${v2CoursePath(context.serverId, context.courseId, tab.id)}${context.selectedCohort ? `?cohort=${encodeURIComponent(context.selectedCohort.id)}` : ''}`}
            className={activeTab === tab.id ? 'active' : undefined}
          >
            {tab.label}{tab.id === 'questions' ? <i /> : null}
          </NavLink>
        ))}
      </nav>
    </header>
  )
}
