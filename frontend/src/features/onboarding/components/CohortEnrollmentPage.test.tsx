import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { CohortEnrollmentPage } from './CohortEnrollmentPage'
const mocks = vi.hoisted(() => ({
  loading: false, allowed: false, navigationError: false, inviteError: false, rosterError: false,
  totalCount: 20,
  retryNavigation: vi.fn(), retryInvite: vi.fn(), retryRoster: vi.fn(),
  invite: vi.fn(() => ({ isError: mocks.inviteError, refetch: mocks.retryInvite, data: { cohortId: 'one', inviteCode: 'test-invite' } })),
  roster: vi.fn(() => ({ isSuccess: !mocks.rosterError, isError: mocks.rosterError, refetch: mocks.retryRoster, data: { totalCount: mocks.totalCount, enrollments: [{ learnerUserId: 'learner-123456789', enrolledAt: '2026-09-20T10:00:00Z' }] } })),
  enrollment: vi.fn(() => ({ learnerEmail: '', setLearnerEmail: vi.fn(), enroll: vi.fn(), reset: vi.fn(), isSubmitting: false, error: null, successMessage: null })),
}))
vi.mock('../../shell/hooks/use-shell-queries', () => ({ useStudyServerNavigationQuery: () => ({ isLoading: mocks.loading, isError: mocks.navigationError, refetch: mocks.retryNavigation, data: { courses: [{ id: 'course', title: 'Course', capabilities: { canManagePeople: mocks.allowed }, cohorts: [{ id: 'one', name: 'First cohort' }, { id: 'two', name: 'Second cohort' }], channels: [{ id: 'custom', name: 'Project workshop', kind: 'TEXT' }] }] } }) }))
vi.mock('../hooks/use-cohort-enrollments', () => ({ useCohortInvite: mocks.invite, useCohortEnrollments: mocks.roster }))
vi.mock('../hooks/use-cohort-enrollment', () => ({ useCohortEnrollment: mocks.enrollment }))
function Destination() { const location = useLocation(); return <p>{location.pathname}{location.search}</p> }
function pageTree(query = '?cohort=two') {
  return <QueryClientProvider client={new QueryClient()}><MemoryRouter initialEntries={[`/app/servers/study/courses/course/enrollment${query}`]}><Routes><Route path="/app/servers/:serverId/courses/:courseId/enrollment" element={<CohortEnrollmentPage />} /><Route path="/app/servers/:serverId/courses/:courseId/people" element={<Destination />} /></Routes></MemoryRouter></QueryClientProvider>
}
function renderPage(query = '?cohort=two') { return render(pageTree(query)) }
beforeEach(() => { mocks.totalCount = 20; mocks.loading = false; mocks.allowed = false; mocks.navigationError = false; mocks.inviteError = false; mocks.rosterError = false; vi.clearAllMocks() })
afterEach(cleanup)
it('does not start management queries before capabilities load', () => {
  mocks.loading = true
  renderPage()
  expect(mocks.invite).not.toHaveBeenCalled()
  expect(mocks.roster).not.toHaveBeenCalled()
  expect(mocks.enrollment).not.toHaveBeenCalled()
})
it('sends visible non-managers to People with their selected cohort and no management queries', async () => {
  renderPage()
  expect(await screen.findByText('/app/servers/study/courses/course/people?cohort=two')).toBeVisible()
  expect(mocks.invite).not.toHaveBeenCalled()
  expect(mocks.roster).not.toHaveBeenCalled()
  expect(mocks.enrollment).not.toHaveBeenCalled()
})
it('retains manager pagination, cohort switching, search and custom channel links', async () => {
  mocks.allowed = true
  const user = userEvent.setup()
  renderPage()
  expect(screen.getByRole('combobox', { name: 'Cohort' })).toHaveValue('two')
  expect(mocks.invite).toHaveBeenLastCalledWith('two')
  expect(screen.getByRole('link', { name: 'Preview' })).toHaveAttribute('href', '/app/servers/study/course-channels/custom')
  await user.click(screen.getByRole('button', { name: 'Next' }))
  expect(mocks.roster).toHaveBeenLastCalledWith('two', { limit: 8, offset: 8, search: undefined })
  await user.selectOptions(screen.getByRole('combobox', { name: 'Cohort' }), 'one')
  expect(mocks.roster).toHaveBeenLastCalledWith('one', { limit: 8, offset: 0, search: undefined })
  await user.type(screen.getByRole('textbox', { name: 'Search learners' }), 'learner-123')
  await waitFor(() => expect(mocks.roster).toHaveBeenLastCalledWith('one', { limit: 8, offset: 0, search: 'learner-123' }))
})

it('resolves an unavailable cohort bookmark before any management request', () => {
  mocks.allowed = true
  renderPage('?cohort=missing')
  expect(screen.getByRole('combobox', { name: 'Cohort' })).toHaveValue('one')
  expect(mocks.invite).toHaveBeenLastCalledWith('one')
  expect(mocks.enrollment).toHaveBeenLastCalledWith('one')
})

it('requests the surviving first page after the roster shrinks on a later page', async () => {
  mocks.allowed = true
  const user = userEvent.setup()
  const view = renderPage()
  await user.click(screen.getByRole('button', { name: 'Next' }))
  expect(mocks.roster).toHaveBeenLastCalledWith('two', { limit: 8, offset: 8, search: undefined })
  mocks.totalCount = 8
  mocks.rosterError = true
  view.rerender(pageTree())
  expect(mocks.roster).toHaveBeenLastCalledWith('two', { limit: 8, offset: 8, search: undefined })
  mocks.rosterError = false
  view.rerender(pageTree())
  expect(mocks.roster).toHaveBeenLastCalledWith('two', { limit: 8, offset: 0, search: undefined })
})

it.each([
  ['navigationError', 'Retry enrollment', 'retryNavigation'],
  ['rosterError', 'Retry learner list', 'retryRoster'],
  ['inviteError', 'Retry invite link', 'retryInvite'],
] as const)('retries only the failed %s read', async (failure, label, retry) => {
  mocks.allowed = true
  mocks[failure] = true
  renderPage()
  await userEvent.setup().click(screen.getByRole('button', { name: label }))
  expect(mocks[retry]).toHaveBeenCalledTimes(1)
  for (const other of ['retryNavigation', 'retryRoster', 'retryInvite'] as const) {
    if (other !== retry) expect(mocks[other]).not.toHaveBeenCalled()
  }
})
