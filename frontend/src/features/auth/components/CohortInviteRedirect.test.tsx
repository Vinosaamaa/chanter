import { StrictMode } from 'react'
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { CohortInviteRedirect } from './CohortInviteRedirect'
import { rememberCohortInviteFromSearch } from '../../onboarding/cohort-invite'
import * as onboardingApi from '../../onboarding/onboarding-api'

afterEach(() => { cleanup(); sessionStorage.clear(); vi.restoreAllMocks() })

it('waits for the retained invitation across StrictMode effect replay', async () => {
  rememberCohortInviteFromSearch('?cohort=retained-cohort&invite=retained-invite')
  let resolve!: () => void
  const join = vi.spyOn(onboardingApi, 'joinCohort').mockImplementation(() => new Promise<void>(done => { resolve = done }))
  render(<StrictMode><MemoryRouter initialEntries={['/continue']}><Routes>
    <Route path="/continue" element={<CohortInviteRedirect to="/done" />} />
    <Route path="/done" element={<p>Enrollment continuation finished</p>} />
  </Routes></MemoryRouter></StrictMode>)
  await waitFor(() => expect(join).toHaveBeenCalledOnce())
  expect(screen.queryByText('Enrollment continuation finished')).not.toBeInTheDocument()
  expect(screen.getByText('Joining cohort…')).toBeInTheDocument()
  await act(async () => resolve())
  expect(await screen.findByText('Enrollment continuation finished')).toBeInTheDocument()
  expect(sessionStorage.getItem('chanter:pending-cohort-invite')).toBeNull()
})
