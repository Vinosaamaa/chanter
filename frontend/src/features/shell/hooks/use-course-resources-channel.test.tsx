import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import type { CourseResource } from '../../resources/course-resource-types'
import {
  fetchCourseResourceAccess,
  listCourseResources,
  uploadCourseResource,
  retryCourseResourceIngestion,
} from '../../resources/course-resources-api'
import { useCourseResourcesChannel } from './use-course-resources-channel'

vi.mock('../../resources/course-resources-api', () => ({
  downloadCourseResourceContent: vi.fn(),
  fetchCourseResourceAccess: vi.fn(),
  listCourseResources: vi.fn(),
  resourceAccessDeniedMessage: (error: unknown) =>
    error instanceof Error ? error.message : 'Unable to load course resources.',
  uploadCourseResource: vi.fn(),
  retryCourseResourceIngestion: vi.fn(),
}))

const mockedFetchAccess = vi.mocked(fetchCourseResourceAccess)
const mockedListResources = vi.mocked(listCourseResources)
const mockedUploadResource = vi.mocked(uploadCourseResource)

describe('useCourseResourcesChannel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    useAuthStore.setState({
      accessToken: 'access-token',
      user: { id: 'owner-1', email: 'owner@example.com', displayName: 'Owner' },
    })
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    useAuthStore.getState().clearSession()
  })

  it('queues instructor retry and refreshes the current resource to ready', async () => {
    const failed = resource({ aiApproved: true, status: 'AVAILABLE', ingestionStatus: 'FAILED' })
    const queued = { ...failed, ingestionStatus: 'PENDING' as const }
    const ready = { ...failed, ingestionStatus: 'READY' as const }
    mockedFetchAccess.mockResolvedValue({ courseId: 'course-1', canUploadCourseResource: true, canViewCourseResources: true })
    mockedListResources.mockResolvedValue({ courseResources: [failed] })
    vi.mocked(retryCourseResourceIngestion).mockResolvedValue(queued)
    const { result } = renderHook(() => useCourseResourcesChannel('course-1'))
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    vi.useFakeTimers()
    await act(async () => { await result.current.retryIngestion(failed) })
    expect(retryCourseResourceIngestion).toHaveBeenCalledWith(failed.id)
    expect(result.current.resources).toEqual([queued])
    mockedListResources.mockResolvedValue({ courseResources: [ready] })
    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    expect(result.current.resources).toEqual([ready])
  })

  it('ignores a retry result after navigating into another course', async () => {
    const failed = resource({ aiApproved: true, status: 'AVAILABLE', ingestionStatus: 'FAILED' })
    mockedFetchAccess.mockResolvedValue({ courseId: 'course-1', canUploadCourseResource: true, canViewCourseResources: true })
    mockedListResources.mockResolvedValue({ courseResources: [failed] })
    let finish!: (value: CourseResource) => void
    vi.mocked(retryCourseResourceIngestion).mockReturnValue(new Promise((resolve) => { finish = resolve }))
    const { result, rerender } = renderHook(({ courseId }) => useCourseResourcesChannel(courseId), { initialProps: { courseId: 'course-1' } })
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    let retry!: Promise<void>
    act(() => { retry = result.current.retryIngestion(failed) })
    const next = resource({ id: 'next', courseId: 'course-2', aiApproved: false })
    mockedListResources.mockResolvedValue({ courseResources: [next] })
    rerender({ courseId: 'course-2' })
    await waitFor(() => expect(result.current.resources).toEqual([next]))
    await act(async () => { finish({ ...failed, ingestionStatus: 'PENDING' }); await retry })
    expect(result.current.resources).toEqual([next])
    expect(result.current.retryingResourceId).toBeNull()
  })

  it('does not let a stale same-course poll overwrite a completed retry', async () => {
    const failed = resource({ aiApproved: true, status: 'AVAILABLE', ingestionStatus: 'FAILED' })
    const processing = resource({ id: 'processing', aiApproved: true, status: 'AVAILABLE', ingestionStatus: 'PROCESSING' })
    const queued = { ...failed, ingestionStatus: 'PENDING' as const }
    mockedFetchAccess.mockResolvedValue({ courseId: 'course-1', canUploadCourseResource: true, canViewCourseResources: true })
    mockedListResources.mockResolvedValueOnce({ courseResources: [failed, processing] })
    let finishPoll!: (value: { courseResources: CourseResource[] }) => void
    mockedListResources.mockReturnValueOnce(new Promise((resolve) => { finishPoll = resolve }))
    let finishRetry!: (value: CourseResource) => void
    vi.mocked(retryCourseResourceIngestion).mockReturnValue(new Promise((resolve) => { finishRetry = resolve }))
    vi.useFakeTimers()
    const { result } = renderHook(() => useCourseResourcesChannel('course-1'))
    await act(async () => { await Promise.resolve() })
    expect(result.current.isLoading).toBe(false)
    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    expect(mockedListResources).toHaveBeenCalledTimes(2)
    let retry!: Promise<void>
    act(() => { retry = result.current.retryIngestion(failed) })
    await act(async () => {
      finishRetry(queued)
      finishPoll({ courseResources: [failed, processing] })
      await retry
    })
    expect(result.current.resources).toEqual([queued, processing])
  })

  it('loads durable resources, applies live filters, and keeps successful uploads', async () => {
    const recording = resource({
      id: 'recording-1',
      title: 'Lecture recording',
      fileName: 'lecture.mp4',
      contentType: 'video/mp4',
    })
    const slides = resource({
      id: 'slides-1',
      title: 'Recursion slides',
      fileName: 'recursion.pptx',
      contentType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    })
    mockedFetchAccess.mockResolvedValue({
      courseId: 'course-1',
      canUploadCourseResource: true,
      canViewCourseResources: true,
    })
    mockedListResources.mockResolvedValue({ courseResources: [recording, slides] })

    const { result } = renderHook(() => useCourseResourcesChannel('course-1'))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.resources).toEqual([recording, slides])
    expect(result.current.canUpload).toBe(true)

    act(() => result.current.setActiveFilter('recordings'))
    expect(result.current.filteredResources).toEqual([recording])

    act(() => {
      result.current.setActiveFilter('all')
      result.current.setSearchQuery('recursion')
    })
    expect(result.current.filteredResources).toEqual([slides])

    const uploaded = resource({ id: 'resource-new', title: 'Problem set 1' })
    const file = new File(['problem set'], 'problem-set.pdf', { type: 'application/pdf' })
    mockedUploadResource.mockResolvedValue(uploaded)

    let didUpload = false
    await act(async () => {
      didUpload = await result.current.uploadResource(file, {
        title: 'Problem set 1',
        aiApproved: true,
      })
    })

    expect(didUpload).toBe(true)
    expect(mockedUploadResource).toHaveBeenCalledWith('course-1', file, {
      title: 'Problem set 1',
      aiApproved: true,
    })
    expect(result.current.resources[0]).toEqual(uploaded)
  })

  it('ignores an upload response after course navigation', async () => {
    mockedFetchAccess.mockResolvedValue({ courseId: 'course-1', canUploadCourseResource: true, canViewCourseResources: true })
    mockedListResources.mockResolvedValue({ courseResources: [] })
    let finishUpload!: (value: CourseResource) => void
    mockedUploadResource.mockReturnValue(new Promise((resolve) => { finishUpload = resolve }))
    const { result, rerender } = renderHook(({ courseId }) => useCourseResourcesChannel(courseId), { initialProps: { courseId: 'course-1' } })
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    let upload!: Promise<boolean>
    act(() => { upload = result.current.uploadResource(new File(['notes'], 'notes.txt'), { aiApproved: true }) })
    rerender({ courseId: 'course-2' })
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    await act(async () => { finishUpload(resource({})); expect(await upload).toBe(false) })
    expect(result.current.resources).toEqual([])
    expect(result.current.uploadSuccess).toBeNull()
    expect(result.current.isUploading).toBe(false)
  })

  it('clears prior course access and resources when the next course request fails', async () => {
    const existing = resource({ id: 'resource-existing', title: 'Existing notes' })
    mockedFetchAccess.mockResolvedValueOnce({
      courseId: 'course-1',
      canUploadCourseResource: true,
      canViewCourseResources: true,
    })
    mockedListResources.mockResolvedValueOnce({ courseResources: [existing] })

    const { result, rerender } = renderHook(
      ({ courseId }) => useCourseResourcesChannel(courseId),
      { initialProps: { courseId: 'course-1' } },
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.resources).toEqual([existing])

    mockedFetchAccess.mockRejectedValueOnce(new Error('Could not load course resources.'))
    rerender({ courseId: 'course-2' })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.error).toBe('Could not load course resources.')
    expect(result.current.resources).toEqual([])
    expect(result.current.canUpload).toBe(false)
    expect(result.current.canView).toBe(false)
  })
})

function resource(overrides: Partial<CourseResource>): CourseResource {
  return {
    id: 'resource-1',
    courseId: 'course-1',
    title: 'Course resource',
    fileName: 'resource.pdf',
    contentType: 'application/pdf',
    byteSize: 1024,
    aiApproved: false,
    uploadedByUserId: 'owner-1',
    createdAt: '2026-07-13T12:00:00Z',
    ...overrides,
  }
}
