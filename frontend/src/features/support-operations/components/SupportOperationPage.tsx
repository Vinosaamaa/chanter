import { useParams } from 'react-router-dom'

import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import {
  findCourseById,
  findQuestionsChannel,
  isSupportOperation,
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

  if (navigationQuery.isError) {
    return (
      <section className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
        Unable to load course navigation for this Study Server.
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
