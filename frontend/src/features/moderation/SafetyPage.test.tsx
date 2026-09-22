import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import { SafetyPage } from './SafetyPage'

const request = vi.hoisted(() => vi.fn())
vi.mock('../../lib/api-client', () => ({ apiFetch: request }))
vi.mock('../../stores/auth-store', () => ({ useAuthStore: (selector: (state: unknown) => unknown) => selector({ user: { id: 'viewer' } }) }))
afterEach(() => { cleanup(); request.mockReset() })

it('preserves a failed report draft and submits the exact contextual source', async () => {
  const source = 'b36615f9-7f96-46ee-ac7f-0fc937e03b83'
  let fail = true
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (init?.method === 'POST') {
      if (fail) throw new Error('Unavailable')
      return { id: 'report-one', status: 'NEW' }
    }
    return path === '/api/v1/user-blocks' ? { blockedUserIds: [] } : []
  })
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[`/app/safety?type=MESSAGE&id=${source}`]}><SafetyPage /></MemoryRouter>
  </QueryClientProvider>)
  await userEvent.type(screen.getByLabelText('Reason for reporting'), 'This message contains targeted harassment.')
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Your report was not saved')
  expect(screen.getByLabelText('Reason for reporting')).toHaveValue('This message contains targeted harassment.')
  fail = false
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }))
  expect(await screen.findByText('Your report was saved for review.')).toBeVisible()
  expect(request).toHaveBeenCalledWith('/api/v1/moderation/reports', expect.objectContaining({
    body: JSON.stringify({ targetType: 'MESSAGE', targetId: source, reason: 'This message contains targeted harassment.' }),
  }))
})
