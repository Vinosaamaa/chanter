import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { CourseOverviewPage } from './CourseOverviewPage'

const workspace = vi.hoisted(() => ({
  serverId: 'server-1',
  courseId: 'course-1',
  course: {
    channels: [{ id: 'voice-1', cohortId: 'cohort-1', name: 'study-room', kind: 'VOICE' as const }],
  },
  selectedCohort: { id: 'cohort-1', name: 'Summer 2026' },
}))

vi.mock('../../layouts/v2-course-workspace-context', () => ({
  useV2CourseWorkspace: () => workspace,
}))

describe('CourseOverviewPage', () => {
  it('deep-links Study room to the selected Cohort voice channel', () => {
    render(
      <MemoryRouter>
        <CourseOverviewPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Join Study room' })).toHaveAttribute(
      'href',
      '/app/servers/server-1/courses/course-1/chat?cohort=cohort-1&channel=voice-1',
    )
  })
})
