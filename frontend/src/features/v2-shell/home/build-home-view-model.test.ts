import { describe, expect, it } from 'vitest'

import { buildHomeViewModel } from './build-home-view-model'
import type { HomeSummaryResponse } from '../../home/home-summary-types'

const sampleSummary: HomeSummaryResponse = {
  courses: [
    {
      courseId: 'c1',
      studyServerId: 's1',
      title: 'CS 101 — Intro to CS',
      cohortId: 'co1',
      cohortName: 'Spring cohort',
      instructorDisplayName: 'Dr. Ada',
      progress: null,
      progressUnavailableReason: 'NO_CURRICULUM',
      href: '/app/servers/s1/courses/c1/overview?cohort=co1',
    },
    {
      courseId: 'c2',
      studyServerId: 's1',
      title: 'MATH 201 — Linear Algebra',
      cohortId: 'co2',
      cohortName: 'Spring cohort',
      instructorDisplayName: null,
      progress: null,
      progressUnavailableReason: 'NO_CURRICULUM',
      href: '/app/servers/s1/courses/c2/overview?cohort=co2',
    },
  ],
  attention: [
    {
      id: 'oh-1',
      kind: 'OFFICE_HOURS',
      headline: 'Office hours',
      suffix: ' · CS 101 · 2:00 PM',
      actionLabel: 'Join',
      actionVariant: 'button',
      href: '/app/servers/s1/courses/c1/office-hours?cohort=co1&session=oh-1',
    },
  ],
  upNext: [
    {
      id: 'oh-up-1',
      kind: 'OFFICE_HOURS',
      title: '2:00 PM',
      detail: 'Office hours — CS 101',
      actionLabel: 'Join',
      href: '/app/servers/s1/courses/c1/office-hours?cohort=co1&session=oh-1',
    },
  ],
  partialFailures: [],
}

describe('buildHomeViewModel', () => {
  it('maps API summary into greeting, courses, attention, and up next', () => {
    const model = buildHomeViewModel('Sam', sampleSummary, new Date('2026-07-11T20:00:00'))

    expect(model.greeting).toBe('Good evening, Sam')
    expect(model.dateLabel).toContain('July')
    expect(model.attention).toHaveLength(1)
    expect(model.attention[0]?.kind).toBe('office')
    expect(model.courses).toHaveLength(2)
    expect(model.courses[0]?.professor).toBe('Dr. Ada')
    expect(model.courses[0]?.progress).toBeNull()
    expect(model.courses[0]?.href).toContain('/overview?cohort=co1')
    expect(model.upNext).toHaveLength(1)
    expect(model.upNext[0]?.href).toContain('session=oh-1')
  })

  it('does not invent unread message, resource, or announcement counts', () => {
    const model = buildHomeViewModel('Sam', sampleSummary, new Date('2026-07-11T20:00:00'))

    expect(model.courses[0]).not.toHaveProperty('messageSummary')
    expect(model.courses[0]).not.toHaveProperty('resourceSummary')
    expect(model.attention.every((item) => !/\bunread\b/i.test(item.headline + (item.suffix ?? '')))).toBe(
      true,
    )
  })

  it('returns empty attention and up next when summary is empty', () => {
    const model = buildHomeViewModel('Sam', {
      courses: [],
      attention: [],
      upNext: [],
      partialFailures: [],
    })
    expect(model.attention).toEqual([])
    expect(model.upNext).toEqual([])
    expect(model.courses).toEqual([])
  })
})
