import { describe, expect, it } from 'vitest'

import {
  allGrantKeysFromPreview,
  grantKey,
  grantsFromSelectedKeys,
} from './study-assistant-grants'
import type { StudyAssistantInstallPreview } from './study-assistant-types'

const preview: StudyAssistantInstallPreview = {
  studyServerId: 'server-1',
  alreadyInstalled: false,
  candidates: {
    studyServerId: 'server-1',
    studyServerChannels: [{ id: 'ss-ch-1', name: 'lobby', kind: 'TEXT' }],
    courses: [
      {
        id: 'course-1',
        title: 'Spring Boot',
        cohorts: [{ id: 'cohort-1', name: 'March 2026' }],
        channels: [{ id: 'course-ch-1', name: 'questions', kind: 'TEXT' }],
      },
    ],
  },
  courseResources: [
    {
      id: 'resource-1',
      courseId: 'course-1',
      title: 'Week 1 Slides',
      fileName: 'week-1.pdf',
      aiApproved: true,
    },
  ],
}

describe('study-assistant-grants', () => {
  it('builds stable grant keys', () => {
    expect(grantKey('COURSE_CHANNEL', 'course-ch-1')).toBe('COURSE_CHANNEL:course-ch-1')
  })

  it('collects every install candidate grant key from preview', () => {
    expect(allGrantKeysFromPreview(preview)).toEqual(
      new Set([
        'STUDY_SERVER_CHANNEL:ss-ch-1',
        'COURSE:course-1',
        'COHORT:cohort-1',
        'COURSE_CHANNEL:course-ch-1',
        'COURSE_RESOURCE:resource-1',
      ]),
    )
  })

  it('maps selected keys back to confirmed grants', () => {
    const grants = grantsFromSelectedKeys(preview, new Set(['COURSE_CHANNEL:course-ch-1', 'COURSE_RESOURCE:resource-1']))

    expect(grants).toEqual([
      { grantType: 'COURSE_CHANNEL', grantTargetId: 'course-ch-1' },
      { grantType: 'COURSE_RESOURCE', grantTargetId: 'resource-1' },
    ])
  })
})
