import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AppealPage } from './AppealPage'

const request = vi.hoisted(() => vi.fn())
vi.mock('../../lib/api-client', () => ({ apiFetch: request }))
afterEach(cleanup)
beforeEach(() => { request.mockReset(); window.history.replaceState(null, '', '/appeal'); })

it('requests a verified email link without claiming account ownership or signing in', async () => {
  request.mockResolvedValue(undefined)
  render(<MemoryRouter><AppealPage /></MemoryRouter>)
  await userEvent.type(screen.getByLabelText('Account email'), 'person@example.test')
  await userEvent.type(screen.getByLabelText('Restriction reference'), 'e972fdcf-4228-4f72-a49b-9f4c47ed1e40')
  await userEvent.click(screen.getByRole('button', { name: 'Send appeal link' }))
  expect(await screen.findByRole('status')).toHaveTextContent('If this verified email owns the restriction')
  expect(request).toHaveBeenCalledWith('/api/v1/auth/moderation-appeals/request', expect.objectContaining({
    method: 'POST', skipAuthRefresh: true, body: JSON.stringify({ email: 'person@example.test', restrictionId: 'e972fdcf-4228-4f72-a49b-9f4c47ed1e40' }),
  }))
})

it('keeps an unsuccessful appeal available for retry and clears the link token from the address', async () => {
  window.history.replaceState(null, '', '/appeal#token=verified-link')
  request.mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValueOnce(undefined)
  render(<MemoryRouter><AppealPage /></MemoryRouter>)
  expect(window.location.hash).toBe('')
  await userEvent.type(screen.getByLabelText('Why should this be reviewed?'), 'This restriction affects the wrong resource.')
  await userEvent.click(screen.getByRole('button', { name: 'Send appeal' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Your appeal could not be confirmed')
  expect(screen.getByLabelText('Why should this be reviewed?')).toHaveValue('This restriction affects the wrong resource.')
  await userEvent.click(screen.getByRole('button', { name: 'Send appeal' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Your appeal was saved')
})
