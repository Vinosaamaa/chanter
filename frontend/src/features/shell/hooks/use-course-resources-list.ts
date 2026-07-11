import { useEffect, useMemo, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import type { CourseResource } from '../../resources/course-resource-types'
import { listCourseResources } from '../../resources/course-resources-api'

export function useCourseResourcesList(courseId: string) {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const [resources, setResources] = useState<CourseResource[]>([])
  const [canView, setCanView] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loadedKey, setLoadedKey] = useState<string | null>(null)

  const requestKey = courseId && userId ? `${courseId}:${userId}` : null
  const isLoading = requestKey !== null && loadedKey !== requestKey

  useEffect(() => {
    if (!requestKey || !courseId || !userId) {
      return
    }

    let cancelled = false

    void (async () => {
      try {
        const list = await listCourseResources(courseId)
        if (cancelled) {
          return
        }
        setResources(list.courseResources)
        setCanView(true)
        setError(null)
        setLoadedKey(requestKey)
      } catch (caught) {
        if (cancelled) {
          return
        }
        if (caught instanceof ApiError && caught.status === 403) {
          setCanView(false)
          setResources([])
          setError(null)
        } else {
          setCanView(false)
          setResources([])
          setError('Could not load course resources.')
        }
        setLoadedKey(requestKey)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [courseId, requestKey, userId])

  const activeResources = useMemo(
    () => (requestKey ? resources : []),
    [requestKey, resources],
  )
  const activeCanView = requestKey ? canView : false

  const aiApprovedCount = useMemo(
    () => activeResources.filter((resource) => resource.aiApproved).length,
    [activeResources],
  )

  const recentResources = useMemo(
    () =>
      [...activeResources]
        .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
        .slice(0, 5),
    [activeResources],
  )

  return {
    resources: activeResources,
    canView: activeCanView,
    error: requestKey ? error : null,
    isLoading: requestKey ? isLoading : false,
    aiApprovedCount,
    recentResources,
  }
}
