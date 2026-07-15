import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'
import {
  courseOverviewSummaryQueryKey,
  fetchCourseOverviewSummary,
} from './course-overview-summary-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('course-overview-summary-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('fetches overview summary with cohort query', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      progress: null,
      progressUnavailableReason: 'NO_CURRICULUM',
      thisWeek: [],
      recentActivity: [],
      upNext: [],
      partialFailures: [],
    })
    await fetchCourseOverviewSummary('course-1', 'cohort-1')
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/v1/courses/course-1/overview-summary?cohortId=cohort-1',
    )
  })

  it('omits cohort query when absent', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      progress: null,
      progressUnavailableReason: 'NO_CURRICULUM',
      thisWeek: [],
      recentActivity: [],
      upNext: [],
      partialFailures: [],
    })
    await fetchCourseOverviewSummary('course-1')
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/courses/course-1/overview-summary')
  })

  it('builds a stable query key', () => {
    expect(courseOverviewSummaryQueryKey('c1', 'co1')).toEqual([
      'course-overview-summary',
      'c1',
      'co1',
    ])
  })
})
