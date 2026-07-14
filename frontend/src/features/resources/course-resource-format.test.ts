import { describe, expect, it } from 'vitest'

import type { CourseResource } from './course-resource-types'
import { resourceFileKind, resourceKindLabel } from './course-resource-format'

describe('course resource formatting', () => {
  it('classifies only audio and video files as recordings', () => {
    expect(resourceFileKind(resource({ fileName: 'lecture.mp4', contentType: 'video/mp4' }))).toBe(
      'recordings',
    )
    expect(resourceFileKind(resource({ fileName: 'discussion.m4a', contentType: 'audio/mp4' }))).toBe(
      'recordings',
    )
    expect(resourceKindLabel('recordings')).toBe('Recording')
    expect(resourceFileKind(resource({ fileName: 'starter-code.zip', contentType: 'application/zip' }))).toBe(
      'other',
    )
  })
})

function resource(overrides: Partial<CourseResource>): CourseResource {
  return {
    id: 'resource-1',
    courseId: 'course-1',
    title: 'Course resource',
    fileName: 'resource.bin',
    contentType: 'application/octet-stream',
    byteSize: 10,
    aiApproved: false,
    uploadedByUserId: 'owner-1',
    createdAt: '2026-07-13T12:00:00Z',
    ...overrides,
  }
}
