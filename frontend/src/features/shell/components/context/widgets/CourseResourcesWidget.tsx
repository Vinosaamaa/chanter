import { Link } from 'react-router-dom'

import { useAuthStore } from '../../../../../stores/auth-store'
import { useCourseResourcesList } from '../../../hooks/use-course-resources-list'
import { courseChannelPath } from '../../../shell-routes'

import { ContextWidgetSection } from '../ContextPanelFrame'

function resourceIcon(fileName: string): string {
  const lower = fileName.toLowerCase()
  if (lower.endsWith('.pdf')) {
    return '📄'
  }
  if (lower.endsWith('.md') || lower.endsWith('.txt')) {
    return '📝'
  }
  return '📎'
}

export function CourseResourcesWidget({
  serverId,
  courseId,
  courseTitle,
  resourcesChannelId,
  limit = 5,
}: {
  serverId: string
  courseId: string
  courseTitle: string
  resourcesChannelId: string | null
  limit?: number
}) {
  const userId = useAuthStore((state) => state.user?.id)
  const { resources, canView, error, isLoading } = useCourseResourcesList(courseId)

  if (!userId) {
    return null
  }

  if (!isLoading && error) {
    return (
      <ContextWidgetSection title="Course resources">
        <p className="text-xs text-rose-200">{error}</p>
      </ContextWidgetSection>
    )
  }

  if (!canView && !isLoading) {
    return null
  }

  const visible = resources.slice(0, limit)

  return (
    <ContextWidgetSection
      title="Course resources"
      action={
        resourcesChannelId ? (
          <Link
            to={courseChannelPath(serverId, resourcesChannelId)}
            className="text-[11px] font-medium text-app-accent hover:text-app-accent-hover"
          >
            Open hub
          </Link>
        ) : null
      }
    >
      {isLoading ? (
        <p className="text-xs text-app-muted">Loading resources…</p>
      ) : visible.length === 0 ? (
        <p className="text-xs text-app-muted">No resources uploaded for {courseTitle} yet.</p>
      ) : (
        <ul className="space-y-2">
          {visible.map((resource) => (
            <li key={resource.id} className="flex items-start gap-2 text-xs text-app-text">
              <span aria-hidden>{resourceIcon(resource.fileName)}</span>
              <span className="min-w-0">
                <span className="block truncate font-medium">{resource.title}</span>
                <span className="block truncate text-app-muted">{resource.fileName}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </ContextWidgetSection>
  )
}
