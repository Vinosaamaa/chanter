import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch, apiFetchBlob } from '../../lib/api-client'
import {
  cancelCommunityEvent,
  createCommunityEvent,
  downloadCommunityEventIcs,
  fetchCommunityEvents,
  upsertCommunityEventRsvp,
} from './community-events-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
  apiFetchBlob: vi.fn(),
}))

describe('community-events-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
    vi.mocked(apiFetchBlob).mockReset()
  })

  it('lists events with filter query', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ events: [] })
    await fetchCommunityEvents('server-1', 'GOING')
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/events?filter=GOING')
  })

  it('creates events with POST body', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 'event-1' })
    await createCommunityEvent('server-1', {
      title: 'Hackathon',
      startsAt: '2026-07-18T14:00:00Z',
      endsAt: '2026-07-18T18:00:00Z',
      visibility: 'HUB',
    })
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/events', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: 'Hackathon',
        startsAt: '2026-07-18T14:00:00Z',
        endsAt: '2026-07-18T18:00:00Z',
        visibility: 'HUB',
      }),
    })
  })

  it('upserts RSVP and cancels events', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 'event-1' })
    await upsertCommunityEventRsvp('server-1', 'event-1', 'GOING')
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/study-servers/server-1/events/event-1/rsvp', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'GOING' }),
    })
    await cancelCommunityEvent('server-1', 'event-1')
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/v1/study-servers/server-1/events/event-1/cancel',
      { method: 'POST' },
    )
  })

  it('downloads ICS export', async () => {
    vi.mocked(apiFetchBlob).mockResolvedValue(new Blob(['BEGIN:VCALENDAR']))
    await downloadCommunityEventIcs('server-1', 'event-1')
    expect(apiFetchBlob).toHaveBeenCalledWith('/api/v1/study-servers/server-1/events/event-1/ics')
  })
})
