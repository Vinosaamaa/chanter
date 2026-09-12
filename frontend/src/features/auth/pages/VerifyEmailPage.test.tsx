import { StrictMode } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'

import { VerifyEmailPage } from './VerifyEmailPage'

const verifyEmail = vi.hoisted(() => vi.fn())
vi.mock('../auth-api', () => ({ verifyEmail }))
afterEach(cleanup)

it('consumes an email verification link only once under StrictMode', async () => {
  verifyEmail.mockResolvedValueOnce({ message: 'Email verified. You can sign in.' })
    .mockRejectedValue(new Error('This verification token was already used.'))
  render(<StrictMode><MemoryRouter initialEntries={['/verify-email?token=one-time-token']}>
    <VerifyEmailPage />
  </MemoryRouter></StrictMode>)
  expect(await screen.findByRole('status')).toHaveTextContent('Email verified')
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(verifyEmail).toHaveBeenCalledTimes(1)
})
