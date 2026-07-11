import { useEffect, useState } from 'react'

import { useAuthStore } from '../../../../stores/auth-store'
import type { CourseResource } from '../../../resources/course-resource-types'
import {
  fetchCourseResourceAccess,
  listCourseResources,
} from '../../../resources/course-resources-api'
import type { ShellCourse } from '../../types'

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
  const [resources, setResources] = useState<CourseResource[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (!userId) {
      return
    }

    let cancelled = false

    void (async () => {
      setIsLoading(true)
      try {
        const access = await fetchCourseResourceAccess(course.id)
        if (!access.canViewCourseResources) {
          setResources([])
          return
        }
        const list = await listCourseResources(course.id)
        if (!cancelled) {
          setResources(list.courseResources)
        }
      } catch {
        if (!cancelled) {
          setResources([])
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [course.id, userId])

  const aiApprovedCount = resources.filter((resource) => resource.aiApproved).length
  const recent = [...resources]
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
    .slice(0, 5)

  return (
    <ContextPanelFrame eyebrow="Resources" title={course.title}>
      <ContextWidgetSection title="Approved for AI">
        {isLoading ? (
          <p className="text-xs text-app-muted">Loading resource summary…</p>
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
        {isLoading ? (
          <p className="text-xs text-app-muted">Loading uploads…</p>
        ) : recent.length === 0 ? (
          <p className="text-xs text-app-muted">Upload materials in the main panel to get started.</p>
        ) : (
          <ul className="space-y-2">
            {recent.map((resource) => (
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
