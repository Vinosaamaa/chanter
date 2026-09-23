import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { StudyServerHomePage } from './StudyServerHomePage'

const create = vi.hoisted(() => vi.fn())
vi.mock('../onboarding-api', () => ({ createCourse: create }))
vi.mock('../../shell/hooks/use-shell-queries', () => ({ useStudyServerNavigationQuery: () => ({ data: {
  studyServerName: 'Field learning', capabilities: { canCreateCourse: true }, courses: [],
} }) }))
beforeEach(() => { create.mockReset(); create.mockResolvedValue({ id: 'created-course' }) })
afterEach(cleanup)

it.each(['INVITE_ONLY', 'OPEN'] as const)('creates a cohort with explicit %s access', async policy => {
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter initialEntries={['/app/servers/study/home']}><Routes>
    <Route path="/app/servers/:serverId/home" element={<StudyServerHomePage />} />
  </Routes></MemoryRouter></QueryClientProvider>)
  const user = userEvent.setup()
  const access = screen.getByRole('combobox', { name: 'Who can join' })
  expect(access).toHaveValue('INVITE_ONLY')
  await user.type(screen.getByLabelText('Course title'), 'Field observation')
  await user.type(screen.getByLabelText('Cohort name'), 'Weekend group')
  if (policy === 'OPEN') await user.selectOptions(access, policy)
  await user.click(screen.getByRole('button', { name: 'Create course' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Created Field observation (Weekend group).')
  expect(create).toHaveBeenCalledWith('study', { title: 'Field observation', cohortName: 'Weekend group', enrollmentPolicy: policy })
})
