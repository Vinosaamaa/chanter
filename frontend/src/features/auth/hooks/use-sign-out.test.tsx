import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { useSignOut } from './use-sign-out'

const signOut = vi.hoisted(() => vi.fn())
vi.mock('../browser-session', () => ({ signOutBrowserSession: signOut }))
afterEach(() => { cleanup(); vi.resetAllMocks() })
function Control({ returnTo }: { returnTo?: string }) {
  const logout = useSignOut(returnTo)
  return <button onClick={() => void logout()}>Reauthenticate</button>
}
function Destination() { return <output>{JSON.stringify(useLocation().state)}</output> }
for (const failed of [false, true]) {
  it(`keeps the explicit deletion return path when logout ${failed ? 'fails' : 'succeeds'}`, async () => {
    if (failed) signOut.mockRejectedValue(new Error('offline'))
    else signOut.mockResolvedValue(undefined)
    const returnTo = '/app/account-data/delete?job=b633c892-6762-40ec-a945-b042957a052b'
    render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><Routes>
      <Route path="/" element={<Control returnTo={returnTo} />} />
      <Route path="/sign-in" element={<Destination />} />
    </Routes></MemoryRouter></QueryClientProvider>)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reauthenticate' }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(JSON.stringify({ from: returnTo, ...(failed ? { logoutFailed: true } : {}) })))
  })
}
