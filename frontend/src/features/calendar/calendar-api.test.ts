import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'
import { calendarQueryKey, fetchCalendar } from './calendar-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('calendar-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('fetches calendar with from/to/types/search', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ items: [], notes: [] })
    await fetchCalendar({
      from: '2026-07-01T00:00:00.000Z',
      to: '2026-08-01T00:00:00.000Z',
      types: 'OFFICE_HOURS',
      search: 'office',
    })
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/v1/me/calendar?from=2026-07-01T00%3A00%3A00.000Z&to=2026-08-01T00%3A00%3A00.000Z&types=OFFICE_HOURS&search=office',
    )
  })

  it('builds a stable query key', () => {
    expect(
      calendarQueryKey('user-1', {
        from: 'a',
        to: 'b',
        types: 'EVENT',
        search: 'talk',
      }),
    ).toEqual(['calendar', 'user-1', 'a', 'b', 'EVENT', 'talk'])
  })
})
