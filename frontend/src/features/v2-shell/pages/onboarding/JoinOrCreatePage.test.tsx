import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { JoinOrCreatePage } from './JoinOrCreatePage'

const onboardingApi = vi.hoisted(() => ({ joinCohort: vi.fn() }))
vi.mock('../../../onboarding/onboarding-api', () => onboardingApi)

describe('JoinOrCreatePage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    onboardingApi.joinCohort.mockResolvedValue(undefined)
  })

  it('joins from a copied invite link and refreshes shell navigation', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(['study-servers'], [{ id: 'server-old' }])
    queryClient.setQueryData(['study-server-navigation', 'server-old'], { courses: [] })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/app/onboarding/join-or-create']}>
          <JoinOrCreatePage />
          <LocationProbe />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByRole('link', { name: 'Create a Study Server' })).toHaveAttribute(
      'href',
      '/app/onboarding/create-study-server',
    )

    await user.type(
      screen.getByRole('textbox', { name: 'Cohort invite link' }),
      'http://localhost:5173/sign-in?cohort=cohort-1&invite=code-1',
    )
    await user.click(screen.getByRole('button', { name: 'Join cohort' }))

    await waitFor(() => expect(onboardingApi.joinCohort).toHaveBeenCalledWith('cohort-1', 'code-1'))
    expect(queryClient.getQueryState(['study-servers'])?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(['study-server-navigation', 'server-old'])?.isInvalidated).toBe(true)
    expect(screen.getByTestId('join-location')).toHaveTextContent('/app/home')
  })
})

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="join-location">{location.pathname}</p>
}
