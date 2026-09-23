import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { OAuthCallbackPage } from './OAuthCallbackPage'
import * as browserSession from '../browser-session'

afterEach(() => { cleanup(); vi.restoreAllMocks() })

it('continues through the authenticated sign-in route so a retained invite can finish', async () => {
  vi.spyOn(browserSession, 'authenticateBrowserSession').mockResolvedValue({ accessToken: 'synthetic-token', expiresInSeconds: 300, user: { id: 'synthetic-user', email: 'learner@example.com', displayName: 'Learner' } })
  render(<MemoryRouter initialEntries={['/oauth/callback/google?code=synthetic-code&state=synthetic-state']}><Routes>
    <Route path="/oauth/callback/google" element={<OAuthCallbackPage />} />
    <Route path="/sign-in" element={<p>Authenticated invitation continuation</p>} />
    <Route path="/app/home" element={<p>Skipped invitation</p>} />
  </Routes></MemoryRouter>)
  expect(await screen.findByText('Authenticated invitation continuation')).toBeInTheDocument()
  expect(screen.queryByText('Skipped invitation')).not.toBeInTheDocument()
})
