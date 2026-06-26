import { Link, useParams } from 'react-router-dom'

import { cn } from '../../../lib/cn'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import {
  findCourseById,
  findQuestionsChannel,
  isSupportOperation,
  SUPPORT_OPERATIONS,
  supportOperationLabel,
  supportOperationPath,
} from '../../shell/shell-routes'

import { FaqApprovalPanel } from './FaqApprovalPanel'
import { OfficeHoursPanel } from './OfficeHoursPanel'
import { TaQueuePanel } from './TaQueuePanel'

export function SupportOperationPage() {
  const { serverId, courseId, operation } = useParams()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const resolvedOperation = isSupportOperation(operation) ? operation : null
  const course = findCourseById(navigationQuery.data, courseId)
  const cohort = course?.cohorts[0]
  const questionsChannel = course ? findQuestionsChannel(course) : undefined

  if (!serverId || !courseId || !resolvedOperation) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Support operation not found.
      </section>
    )
  }

  if (navigationQuery.isLoading) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Loading course…
      </section>
    )
  }

  if (!course) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Course not found on this Study Server.
      </section>
    )
  }

  if (!cohort) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        This course has no cohort yet.
      </section>
    )
  }

  return (
    <div className="flex min-w-0 flex-1 flex-col">
      <nav className="flex gap-1 border-b border-app-border bg-app-surface px-4 py-2">
        {SUPPORT_OPERATIONS.map((item) => (
          <Link
            key={item}
            to={supportOperationPath(serverId, courseId, item)}
            aria-current={resolvedOperation === item ? 'page' : undefined}
            className={cn(
              'rounded-md px-3 py-1.5 text-sm transition-colors',
              resolvedOperation === item
                ? 'bg-app-elevated font-medium text-app-text'
                : 'text-app-muted hover:bg-app-elevated/70 hover:text-app-text',
            )}
          >
            {supportOperationLabel(item)}
          </Link>
        ))}
      </nav>

      {resolvedOperation === 'ta-queue' && (
        <TaQueuePanel
          courseTitle={course.title}
          cohortName={cohort.name}
          cohortId={cohort.id}
        />
      )}
      {resolvedOperation === 'office-hours' && (
        <OfficeHoursPanel
          courseTitle={course.title}
          cohortName={cohort.name}
          cohortId={cohort.id}
        />
      )}
      {resolvedOperation === 'faq-approval' && (
        <FaqApprovalPanel
          courseTitle={course.title}
          courseId={course.id}
          questionsChannelId={questionsChannel?.id}
        />
      )}
    </div>
  )
}
