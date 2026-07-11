import { useAuthStore } from '../../../../stores/auth-store'
import type { ShellCourse } from '../../types'
import { useCourseResourcesList } from '../../hooks/use-course-resources-list'

import { ContextPanelFrame, ContextWidgetSection } from './ContextPanelFrame'
import { ApprovedFaqsWidget } from './widgets/ApprovedFaqsWidget'

export function ResourcesContextPanel({
  serverId,
  course,
}: {
  serverId: string
  course: ShellCourse
}) {
  const userId = useAuthStore((state) => state.user?.id)
  const { aiApprovedCount, canView, isLoading, recentResources } = useCourseResourcesList(course.id)

  return (
    <ContextPanelFrame eyebrow="Resources" title={course.title}>
      <ContextWidgetSection title="Approved for AI">
        {!userId ? (
          <p className="text-xs text-app-muted">Sign in to view resource summary.</p>
        ) : isLoading ? (
          <p className="text-xs text-app-muted">Loading resource summary…</p>
        ) : !canView ? (
          <p className="text-xs text-app-muted">You do not have permission to view course resources.</p>
        ) : (
          <p className="text-xs text-app-text">
            <span className="text-lg font-semibold text-app-accent">{aiApprovedCount}</span>
            <span className="ml-1 text-app-muted">
              AI-approved resource{aiApprovedCount === 1 ? '' : 's'} available to the Study
              Assistant.
            </span>
          </p>
        )}
      </ContextWidgetSection>

      <ContextWidgetSection title="Recent uploads">
        {!userId ? (
          <p className="text-xs text-app-muted">Sign in to view recent uploads.</p>
        ) : isLoading ? (
          <p className="text-xs text-app-muted">Loading uploads…</p>
        ) : !canView ? (
          <p className="text-xs text-app-muted">You do not have permission to view course resources.</p>
        ) : recentResources.length === 0 ? (
          <p className="text-xs text-app-muted">Upload materials in the main panel to get started.</p>
        ) : (
          <ul className="space-y-2">
            {recentResources.map((resource) => (
              <li key={resource.id} className="text-xs text-app-text">
                <span className="font-medium">{resource.title}</span>
                <span className="block text-app-muted">
                  {resource.aiApproved ? 'AI-approved' : 'Not AI-approved'} · {resource.fileName}
                </span>
              </li>
            ))}
          </ul>
        )}
      </ContextWidgetSection>

      <ApprovedFaqsWidget serverId={serverId} courseId={course.id} limit={3} />
    </ContextPanelFrame>
  )
}
