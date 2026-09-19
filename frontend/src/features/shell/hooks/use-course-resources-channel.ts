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
  retryCourseResourceIngestion,
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
  retryIngestion: (resource: CourseResource) => Promise<void>
  retryingResourceId: string | null
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
  const [retryingResourceId, setRetryingResourceId] = useState<string | null>(null)
  const [downloadingResourceId, setDownloadingResourceId] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeFilter, setActiveFilter] = useState<CourseResourceFilter>('all')
  const previewUrlRef = useRef<string | null>(null)
  const resourceRevisionRef = useRef(0)

  const requestKey = courseId && userId ? `${courseId}:${userId}` : null
  const isLoading = requestKey !== null && loadedKey !== requestKey
  const activeRequestKeyRef = useRef(requestKey)
  useEffect(() => { activeRequestKeyRef.current = requestKey }, [requestKey])

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
      setIsUploading(false)
      setRetryingResourceId(null)
      setCanUpload(false)
      setCanView(false)
      setResources([])

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
        setCanUpload(false)
        setCanView(false)
        setResources([])
        setError(resourceAccessDeniedMessage(caught))
        setLoadedKey(requestKey)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [courseId, requestKey, userId])

  useEffect(() => {
    if (!canView || isLoading || isUploading || retryingResourceId || !resources.some((r) => r.status === 'PROCESSING'
      || (r.aiApproved && ['PENDING', 'PROCESSING'].includes(r.ingestionStatus ?? '')))) return
    let cancelled = false
    const timer = window.setTimeout(() => {
      const revision = resourceRevisionRef.current
      void listCourseResources(courseId).then((list) => {
        if (!cancelled && activeRequestKeyRef.current === requestKey && resourceRevisionRef.current === revision) setResources(list.courseResources)
      }).catch((caught: unknown) => {
        if (cancelled || activeRequestKeyRef.current !== requestKey || resourceRevisionRef.current !== revision) return
        setError(resourceAccessDeniedMessage(caught))
        if (caught instanceof ApiError && [403, 404].includes(caught.status)) {
          setCanView(false)
          setCanUpload(false)
          setResources([])
        }
      })
    }, 5000)
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [canView, courseId, isLoading, isUploading, requestKey, resources, retryingResourceId])

  const retryIngestion = useCallback(async (resource: CourseResource) => {
    if (!canUpload || resource.courseId !== courseId || resource.status !== 'AVAILABLE'
      || !resource.aiApproved || resource.ingestionStatus !== 'FAILED' || retryingResourceId) return
    resourceRevisionRef.current += 1
    setRetryingResourceId(resource.id)
    setError(null)
    try {
      const updated = await retryCourseResourceIngestion(resource.id)
      if (activeRequestKeyRef.current !== requestKey) return
      setResources((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (caught) {
      if (activeRequestKeyRef.current === requestKey) setError(resourceAccessDeniedMessage(caught))
    } finally {
      if (activeRequestKeyRef.current === requestKey) setRetryingResourceId(null)
    }
  }, [canUpload, courseId, requestKey, retryingResourceId])

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
      resourceRevisionRef.current += 1
      setError(null)
      setUploadSuccess(null)

      try {
        const created = await uploadCourseResource(courseId, file, options)
        if (activeRequestKeyRef.current !== requestKey) return false
        setResources((current) => {
          if (current.some((resource) => resource.id === created.id)) {
            return current
          }
          return [created, ...current]
        })
        setUploadSuccess(`Uploaded ${created.title}.`)
        return true
      } catch (caught) {
        if (activeRequestKeyRef.current === requestKey) setError(resourceAccessDeniedMessage(caught))
        return false
      } finally {
        if (activeRequestKeyRef.current === requestKey) setIsUploading(false)
      }
    },
    [canUpload, courseId, requestKey, userId],
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
    retryIngestion,
    retryingResourceId,
  }
}
