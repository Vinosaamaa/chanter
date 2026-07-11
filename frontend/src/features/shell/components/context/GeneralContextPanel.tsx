import { Link } from 'react-router-dom'

import type { ShellCourse } from '../../types'
import { findQuestionsChannel, resolveCourseCohortId, supportOperationPath } from '../../shell-routes'

import { ContextPanelFrame, ContextWidgetSection } from './ContextPanelFrame'
import { CourseResourcesWidget } from './widgets/CourseResourcesWidget'
import { TaQueueSummaryWidget } from './widgets/TaQueueSummaryWidget'

export function GeneralContextPanel({
  serverId,
  course,
}: {
  serverId: string
  course: ShellCourse
}) {
  const resourcesChannel = course.channels.find((channel) => channel.name === 'resources')
  const questionsChannel = findQuestionsChannel(course)
  const cohortId = resolveCourseCohortId(course)

  return (
    <ContextPanelFrame eyebrow="Course context" title={course.title}>
      <TaQueueSummaryWidget
        serverId={serverId}
        courseId={course.id}
        cohortId={cohortId}
        ambiguousCohort={course.cohorts.length > 1}
      />
      <CourseResourcesWidget
        serverId={serverId}
        courseId={course.id}
        courseTitle={course.title}
        resourcesChannelId={resourcesChannel?.id ?? null}
      />
      {questionsChannel ? (
        <ContextWidgetSection title="Need help?">
          <p className="text-xs text-app-muted">
            Ask course questions in <strong>#questions</strong> and invoke the AI Study Assistant
            for grounded answers.
          </p>
          <Link
            to={`/app/servers/${serverId}/course-channels/${questionsChannel.id}`}
            className="mt-2 inline-flex text-xs font-medium text-app-accent hover:text-app-accent-hover"
          >
            Open #questions
          </Link>
        </ContextWidgetSection>
      ) : null}
      <ContextWidgetSection title="Office hours">
        <p className="text-xs text-app-muted">Join a live voice session when instructors are available.</p>
        <Link
          to={supportOperationPath(serverId, course.id, 'office-hours')}
          className="mt-2 inline-flex text-xs font-medium text-app-accent hover:text-app-accent-hover"
        >
          View office hours
        </Link>
      </ContextWidgetSection>
    </ContextPanelFrame>
  )
}
