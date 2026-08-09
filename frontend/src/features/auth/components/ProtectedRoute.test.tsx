import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import type { AuthSession } from '../types'
import { ProtectedRoute } from './ProtectedRoute'

describe('ProtectedRoute account transitions', () => {
  afterEach(() => {
    cleanup()
    useAuthStore.getState().clearSession()
  })

  it('does not preserve the previous account route when an active session ends', async () => {
    const user = userEvent.setup()
    useAuthStore.getState().setSession(sessionFor('owner'))

    render(
      <MemoryRouter initialEntries={['/app/servers/owner-server/community/members']}>
        <Routes>
          <Route
            path="/app/*"
            element={(
              <ProtectedRoute>
                <button type="button" onClick={() => useAuthStore.getState().clearSession()}>
                  End session
                </button>
              </ProtectedRoute>
            )}
          />
          <Route path="/sign-in" element={<SignInLocationProbe />} />
        </Routes>
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: 'End session' }))

    await waitFor(() => expect(screen.getByTestId('sign-in-location')).toHaveTextContent('/sign-in'))
    expect(screen.getByTestId('sign-in-state')).toHaveTextContent('null')
  })
})

function SignInLocationProbe() {
  const location = useLocation()
  return (
    <>
      <p data-testid="sign-in-location">{location.pathname}</p>
      <p data-testid="sign-in-state">{JSON.stringify(location.state)}</p>
    </>
  )
}

function sessionFor(userId: string): AuthSession {
  return {
    accessToken: `access-${userId}`,
    refreshToken: `refresh-${userId}`,
    expiresInSeconds: 900,
    user: {
      id: userId,
      email: `${userId}@chanter.local`,
      displayName: userId,
    },
  }
}
