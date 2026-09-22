import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { fetchCommunityEvents, updateCommunityEvent } from '../../../community-events/community-events-api'
import type { CommunityEvent } from '../../../community-events/community-event-types'
import { CommunityEventsPage } from './CommunityEventsPage'

vi.mock('../../../community-events/community-events-api', async () => ({ ...await vi.importActual('../../../community-events/community-events-api'), fetchCommunityEvents: vi.fn(), updateCommunityEvent: vi.fn() }))
vi.mock('../../layouts/v2-community-context', () => ({ useV2Community: () => ({ serverId: 'study', studyServerCapabilities: { canManageEvents: true } }) }))
const event: CommunityEvent = { id: 'event', studyServerId: 'study', title: 'Cohort workshop', description: 'Work through an example', location: 'Library', startsAt: '2026-10-01T14:00:00Z', endsAt: '2026-10-01T15:00:00Z', capacity: 20, visibility: 'COHORT', courseId: 'course', cohortId: 'cohort', createdByUserId: 'owner', status: 'SCHEDULED', goingCount: 2, interestedCount: 1, viewerRsvp: null, canEdit: true, sharePath: '/app/calendar?event=event', calendarPath: '/app/calendar', icsPath: '/event/calendar.ics' }

beforeEach(() => {
  vi.mocked(fetchCommunityEvents).mockResolvedValue({ events: [event] })
  vi.mocked(updateCommunityEvent).mockReset().mockResolvedValue(event)
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() { this.setAttribute('open', '') } })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value() { this.removeAttribute('open') } })
})
afterEach(cleanup)
function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><CommunityEventsPage /></MemoryRouter></QueryClientProvider>)
}
it('opens event details with a keyboard control and restores it after closing', async () => {
  const user = userEvent.setup()
  renderPage()
  const opener = await screen.findByRole('button', { name: 'Cohort workshop' })
  opener.focus()
  await user.keyboard('{Enter}')
  expect(screen.getByRole('dialog', { name: 'Cohort workshop' })).toBeVisible()
  const close = screen.getByRole('button', { name: 'Close event details' })
  expect(close).toHaveFocus()
  await user.click(close)
  expect(opener).toHaveFocus()
})
it('preserves a restricted event audience when saving ordinary edits', async () => {
  const user = userEvent.setup()
  renderPage()
  await user.click(await screen.findByRole('button', { name: 'Edit' }))
  fireEvent.change(screen.getByRole('textbox', { name: 'Event title' }), { target: { value: 'Updated workshop' } })
  await user.click(screen.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(updateCommunityEvent).toHaveBeenCalledWith('study', 'event', expect.objectContaining({ title: 'Updated workshop', visibility: 'COHORT', courseId: 'course', cohortId: 'cohort' })))
})
it('defaults a new event to a future interval and exposes its actual audience', async () => {
  const user = userEvent.setup()
  renderPage()
  await user.click(screen.getByRole('button', { name: 'Create event' }))
  expect(new Date((screen.getByLabelText('Starts') as HTMLInputElement).value).getTime()).toBeGreaterThan(Date.now())
  expect(screen.getByText('Visible to everyone in this Study Server.')).toBeVisible()
  expect(screen.queryByRole('switch')).not.toBeInTheDocument()
})
