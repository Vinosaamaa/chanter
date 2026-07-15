import { apiFetch } from '../../lib/api-client'
import type { CalendarQuery, CalendarResponse } from './calendar-types'

export function calendarQueryKey(userId: string | undefined, query: CalendarQuery) {
  return ['calendar', userId, query.from, query.to, query.types ?? '', query.search ?? ''] as const
}

export function fetchCalendar(query: CalendarQuery): Promise<CalendarResponse> {
  const params = new URLSearchParams({
    from: query.from,
    to: query.to,
  })
  if (query.types) {
    params.set('types', query.types)
  }
  if (query.search?.trim()) {
    params.set('search', query.search.trim())
  }
  return apiFetch<CalendarResponse>(`/api/v1/me/calendar?${params}`)
}
