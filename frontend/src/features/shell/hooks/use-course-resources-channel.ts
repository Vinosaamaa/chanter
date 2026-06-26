import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import type { CourseResource, CourseResourceFilter } from '../../resources/course-resource-types'
import {
  matchesResourceFilter,
  matchesResourceSearch,
  isPdfResource,
} from '../../resources/course-resource-format'
import {
  downloadCourseResourceContent,
  fetchCourseResourceAccess,
  listCourseResources,
  resourceAccessDeniedMessage,
  uploadCourseResource,
} from '../../resources/course-resources-api'

type UseCourseResourcesChannelResult = {
  resources: CourseResource[]
  filteredResources: CourseResource[]
  isLoading: boolean
  accessDenied: boolean
  canUpload: boolean
  canView: boolean
  error: string | null
  uploadSuccess: string | null
  searchQuery: string
  setSearchQuery: (value: string) => void
  activeFilter: CourseResourceFilter
  setActiveFilter: (filter: CourseResourceFilter) => void
  uploadResource: (file: File, options: { title?: string; aiApproved: boolean }) => Promise<boolean>
  isUploading: boolean
  downloadResource: (resource: CourseResource) => Promise<void>
  previewResource: (resource: CourseResource) => Promise<void>
  downloadingResourceId: string | null
  aiApprovedCount: number
}

export function useCourseResourcesChannel(courseId: string): UseCourseResourcesChannelResult {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const [resources, setResources] = useState<CourseResource[]>([])
  const [canUpload, setCanUpload] = useState(false)
  const [canView, setCanView] = useState(false)
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const [downloadingResourceId, setDownloadingResourceId] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeFilter, setActiveFilter] = useState<CourseResourceFilter>('all')
  const previewUrlRef = useRef<string | null>(null)

  const requestKey = courseId && userId ? `${courseId}:${userId}` : null
  const isLoading = requestKey !== null && loadedKey !== requestKey

  useEffect(() => {
    return () => {
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current)
        previewUrlRef.current = null
      }
    }
  }, [])

  useEffect(() => {
    if (!requestKey || !courseId || !userId) {
      return
    }

    let cancelled = false

    void (async () => {
      setError(null)
      setAccessDenied(false)
      setUploadSuccess(null)

      try {
        const access = await fetchCourseResourceAccess(courseId)
        if (cancelled) {
          return
        }

        setCanUpload(access.canUploadCourseResource)
        setCanView(access.canViewCourseResources)

        if (!access.canViewCourseResources) {
          setAccessDenied(true)
          setResources([])
          setLoadedKey(requestKey)
          return
        }

        const list = await listCourseResources(courseId)
        if (cancelled) {
          return
        }

        setResources(list.courseResources)
        setLoadedKey(requestKey)
      } catch (caught) {
        if (cancelled) {
          return
        }

        if (caught instanceof ApiError && caught.status === 403) {
          setAccessDenied(true)
        }
        setError(resourceAccessDeniedMessage(caught))
        setLoadedKey(requestKey)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [courseId, requestKey, userId])

  const filteredResources = useMemo(() => {
    return resources.filter(
      (resource) =>
        matchesResourceFilter(resource, activeFilter) &&
        matchesResourceSearch(resource, searchQuery),
    )
  }, [activeFilter, resources, searchQuery])

  const aiApprovedCount = useMemo(
    () => resources.filter((resource) => resource.aiApproved).length,
    [resources],
  )

  const uploadResource = useCallback(
    async (file: File, options: { title?: string; aiApproved: boolean }) => {
      if (!userId || !canUpload) {
        return false
      }

      setIsUploading(true)
      setError(null)
      setUploadSuccess(null)

      try {
        const created = await uploadCourseResource(courseId, file, options)
        setResources((current) => {
          if (current.some((resource) => resource.id === created.id)) {
            return current
          }
          return [created, ...current]
        })
        setUploadSuccess(`Uploaded ${created.title}.`)
        return true
      } catch (caught) {
        setError(resourceAccessDeniedMessage(caught))
        return false
      } finally {
        setIsUploading(false)
      }
    },
    [canUpload, courseId, userId],
  )

  const downloadResource = useCallback(
    async (resource: CourseResource) => {
      if (!userId || !canView) {
        return
      }

      setDownloadingResourceId(resource.id)
      setError(null)

      try {
        const blob = await downloadCourseResourceContent(resource.id)
        const url = URL.createObjectURL(blob)
        const anchor = document.createElement('a')
        anchor.href = url
        anchor.download = resource.fileName
        anchor.style.display = 'none'
        document.body.appendChild(anchor)
        anchor.click()
        anchor.remove()
        window.setTimeout(() => URL.revokeObjectURL(url), 0)
      } catch (caught) {
        setError(resourceAccessDeniedMessage(caught))
      } finally {
        setDownloadingResourceId(null)
      }
    },
    [canView, userId],
  )

  const previewResource = useCallback(
    async (resource: CourseResource) => {
      if (!userId || !canView) {
        return
      }

      const isPdf = isPdfResource(resource)
      if (!isPdf) {
        return
      }

      setDownloadingResourceId(resource.id)
      setError(null)

      try {
        const blob = await downloadCourseResourceContent(resource.id)
        if (previewUrlRef.current) {
          URL.revokeObjectURL(previewUrlRef.current)
        }
        const url = URL.createObjectURL(blob)
        previewUrlRef.current = url
        window.open(url, '_blank', 'noopener,noreferrer')
      } catch (caught) {
        setError(resourceAccessDeniedMessage(caught))
      } finally {
        setDownloadingResourceId(null)
      }
    },
    [canView, userId],
  )

  return {
    resources,
    filteredResources,
    isLoading,
    accessDenied,
    canUpload,
    canView,
    error,
    uploadSuccess,
    searchQuery,
    setSearchQuery,
    activeFilter,
    setActiveFilter,
    uploadResource,
    isUploading,
    downloadResource,
    previewResource,
    downloadingResourceId,
    aiApprovedCount,
  }
}
