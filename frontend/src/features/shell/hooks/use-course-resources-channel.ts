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
  const viewGenerationRef = useRef(0)
  useEffect(() => {
    activeRequestKeyRef.current = requestKey
    viewGenerationRef.current += 1
    return () => { activeRequestKeyRef.current = null; viewGenerationRef.current += 1 }
  }, [requestKey])

  useEffect(() => {
    return () => {
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current)
        previewUrlRef.current = null
      }
    }
  }, [])

  useEffect(() => {
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
      setDownloadingResourceId(null)
      if (!requestKey || !courseId || !userId) {
        setLoadedKey(null)
        return
      }

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
    if (!requestKey || !canView || isLoading || isUploading || retryingResourceId || !resources.some((r) => r.status === 'PROCESSING'
      || (r.aiApproved && ['PENDING', 'PROCESSING'].includes(r.ingestionStatus ?? '')))) return
    let cancelled = false
    let timer: number
    const refresh = () => {
      const revision = resourceRevisionRef.current
      void listCourseResources(courseId).then((list) => {
        if (!cancelled && activeRequestKeyRef.current === requestKey && resourceRevisionRef.current === revision) {
          setResources(list.courseResources)
          setError(null)
        }
      }).catch((caught: unknown) => {
        if (cancelled || activeRequestKeyRef.current !== requestKey || resourceRevisionRef.current !== revision) return
        setError(resourceAccessDeniedMessage(caught))
        if (caught instanceof ApiError && [403, 404].includes(caught.status)) {
          setCanView(false)
          setCanUpload(false)
          setResources([])
        } else {
          timer = window.setTimeout(refresh, 5000)
        }
      })
    }
    timer = window.setTimeout(refresh, 5000)
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [canView, courseId, isLoading, isUploading, requestKey, resources, retryingResourceId])

  const retryIngestion = useCallback(async (resource: CourseResource) => {
    if (!requestKey || !canUpload || resource.courseId !== courseId || resource.status !== 'AVAILABLE'
      || !resource.aiApproved || resource.ingestionStatus !== 'FAILED' || retryingResourceId) return
    resourceRevisionRef.current += 1
    const viewGeneration = viewGenerationRef.current
    setRetryingResourceId(resource.id)
    setError(null)
    try {
      const updated = await retryCourseResourceIngestion(resource.id)
      if (activeRequestKeyRef.current !== requestKey || viewGenerationRef.current !== viewGeneration) return
      setResources((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (caught) {
      if (activeRequestKeyRef.current === requestKey && viewGenerationRef.current === viewGeneration) setError(resourceAccessDeniedMessage(caught))
    } finally {
      if (activeRequestKeyRef.current === requestKey && viewGenerationRef.current === viewGeneration) setRetryingResourceId(null)
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
      const viewGeneration = viewGenerationRef.current
      setError(null)
      setUploadSuccess(null)

      try {
        const created = await uploadCourseResource(courseId, file, options)
        if (activeRequestKeyRef.current !== requestKey || viewGenerationRef.current !== viewGeneration) return false
        setResources((current) => {
          if (current.some((resource) => resource.id === created.id)) {
            return current
          }
          return [created, ...current]
        })
        setUploadSuccess(`Uploaded ${created.title}.`)
        return true
      } catch (caught) {
        if (activeRequestKeyRef.current === requestKey && viewGenerationRef.current === viewGeneration) setError(resourceAccessDeniedMessage(caught))
        return false
      } finally {
        if (activeRequestKeyRef.current === requestKey && viewGenerationRef.current === viewGeneration) setIsUploading(false)
      }
    },
    [canUpload, courseId, requestKey, userId],
  )

  const downloadResource = useCallback(
    async (resource: CourseResource) => {
      if (!requestKey || !userId || !canView || resource.courseId !== courseId) {
        return
      }

      setDownloadingResourceId(resource.id)
      setError(null)
      const viewGeneration = viewGenerationRef.current

      try {
        const blob = await downloadCourseResourceContent(resource.id)
        if (activeRequestKeyRef.current !== requestKey || viewGenerationRef.current !== viewGeneration) return
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
        if (activeRequestKeyRef.current === requestKey && viewGenerationRef.current === viewGeneration) setError(resourceAccessDeniedMessage(caught))
      } finally {
        if (activeRequestKeyRef.current === requestKey && viewGenerationRef.current === viewGeneration) setDownloadingResourceId(null)
      }
    },
    [canView, courseId, requestKey, userId],
  )

  const previewResource = useCallback(
    async (resource: CourseResource) => {
      if (!requestKey || !userId || !canView || resource.courseId !== courseId) {
        return
      }

      const isPdf = isPdfResource(resource)
      if (!isPdf) {
        return
      }

      setDownloadingResourceId(resource.id)
      setError(null)
      const viewGeneration = viewGenerationRef.current

      try {
        const blob = await downloadCourseResourceContent(resource.id)
        if (activeRequestKeyRef.current !== requestKey || viewGenerationRef.current !== viewGeneration) return
        if (previewUrlRef.current) {
          URL.revokeObjectURL(previewUrlRef.current)
        }
        const url = URL.createObjectURL(blob)
        previewUrlRef.current = url
        window.open(url, '_blank', 'noopener,noreferrer')
      } catch (caught) {
        if (activeRequestKeyRef.current === requestKey && viewGenerationRef.current === viewGeneration) setError(resourceAccessDeniedMessage(caught))
      } finally {
        if (activeRequestKeyRef.current === requestKey && viewGenerationRef.current === viewGeneration) setDownloadingResourceId(null)
      }
    },
    [canView, courseId, requestKey, userId],
  )

  return {
    resources: requestKey ? resources : [],
    filteredResources: requestKey ? filteredResources : [],
    isLoading,
    accessDenied,
    canUpload: requestKey !== null && canUpload,
    canView: requestKey !== null && canView,
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
    aiApprovedCount: requestKey ? aiApprovedCount : 0,
    retryIngestion,
    retryingResourceId,
  }
}
