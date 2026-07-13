import { describe, expect, it } from 'vitest'

import { buildHomeViewModel } from './build-home-view-model'
import type { V2SidebarCourse } from '../hooks/use-v2-sidebar-data'

const sampleCourses: V2SidebarCourse[] = [
  {
    id: 'c1',
    serverId: 's1',
    serverName: 'Spring Bootcamp Hub',
    title: 'CS 101 — Intro to CS',
    cohortLabel: 'Spring cohort',
    accentColor: '#3b82f6',
  },
  {
    id: 'c2',
    serverId: 's1',
    serverName: 'Spring Bootcamp Hub',
    title: 'MATH 201 — Linear Algebra',
    cohortLabel: 'Spring cohort',
    accentColor: '#22c55e',
  },
]

describe('buildHomeViewModel', () => {
  it('builds greeting and caps attention cards at three', () => {
    const model = buildHomeViewModel('Sam', sampleCourses, new Date('2026-07-11T20:00:00'))

    expect(model.greeting).toBe('Good evening, Sam')
    expect(model.dateLabel).toContain('July')
    expect(model.attention).toHaveLength(3)
    expect(model.courses).toHaveLength(2)
    expect(model.upNext.length).toBeGreaterThan(0)
  })

  it('does not invent unread message, resource, or announcement counts', () => {
    const model = buildHomeViewModel('Sam', sampleCourses, new Date('2026-07-11T20:00:00'))

    expect(model.courses[0]).not.toHaveProperty('messageSummary')
    expect(model.courses[0]).not.toHaveProperty('resourceSummary')
    expect(model.attention.find((item) => item.kind === 'announcements')?.headline).toBe('Announcements')
  })
})
