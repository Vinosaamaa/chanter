import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { SignInPage } from './SignInPage'
import * as authApi from '../auth-api'

describe('SignInPage public destinations', () => {
  afterEach(() => { cleanup(); sessionStorage.clear(); vi.restoreAllMocks() })

  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, user: null })
    vi.spyOn(authApi, 'fetchOauthProviders').mockResolvedValue({ providers: [] })
    vi.spyOn(authApi, 'fetchVerificationOptions').mockResolvedValue({ enabled: false, siteKey: null })
  })

  it('exposes Terms, forgot password, and omits unavailable sign-in providers', () => {
    render(
      <MemoryRouter initialEntries={['/sign-in']}>
        <SignInPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Terms' })).toHaveAttribute('href', '/terms')
    expect(screen.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password')
    expect(screen.queryByRole('link', { name: 'Continue with Google' })).not.toBeInTheDocument()
    expect(screen.queryByText(/CHANTER_OAUTH/)).not.toBeInTheDocument()
    expect(screen.queryByText('3 new')).not.toBeInTheDocument()
  })

  it('retains an invitation before authentication and through a bare sign-in return', () => {
    const first = render(<MemoryRouter initialEntries={['/sign-in?cohort=cohort-one&invite=invite-one']}><SignInPage /></MemoryRouter>)
    const expected = JSON.stringify({ cohortId: 'cohort-one', inviteCode: 'invite-one' })
    expect(sessionStorage.getItem('chanter:pending-cohort-invite')).toBe(expected)
    first.unmount()
    render(<MemoryRouter initialEntries={['/sign-in']}><SignInPage /></MemoryRouter>)
    expect(sessionStorage.getItem('chanter:pending-cohort-invite')).toBe(expected)
  })

  it('renders a configured Google provider as a navigation link', async () => {
    const authorizationUrl = 'https://chanter.example.test/api/v1/auth/oauth/google/start'
    vi.mocked(authApi.fetchOauthProviders).mockResolvedValue({ providers: [{ id: 'google', label: 'Google', authorizationUrl }] })
    render(<MemoryRouter initialEntries={['/sign-in']}><SignInPage /></MemoryRouter>)
    expect(await screen.findByRole('link', { name: 'Continue with Google' })).toHaveAttribute('href', authorizationUrl)
  })

  it('gives auth modes tab semantics and uniquely names password actions', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/sign-in']}>
        <SignInPage />
      </MemoryRouter>,
    )

    const signInTab = screen.getByRole('tab', { name: 'Sign in' })
    const registerTab = screen.getByRole('tab', { name: 'Create account' })
    expect(signInTab).toHaveAttribute('aria-selected', 'true')
    expect(registerTab).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByPlaceholderText('••••••••••••••••')).toHaveAccessibleName('Password')
    expect(screen.getByRole('button', { name: 'Show password' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toHaveAttribute('type', 'submit')

    signInTab.focus()
    await user.keyboard('{ArrowRight}')
    expect(registerTab).toHaveFocus()
    expect(signInTab).toHaveAttribute('aria-selected', 'false')
    expect(registerTab).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: 'Create account' })).toHaveAttribute('type', 'submit')
  })
})
