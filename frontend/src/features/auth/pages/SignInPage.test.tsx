import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { SignInPage } from './SignInPage'

describe('SignInPage public destinations', () => {
  afterEach(cleanup)

  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, refreshToken: null, user: null })
  })

  it('exposes Terms, forgot password, and marks unavailable Google sign-in as disabled', () => {
    render(
      <MemoryRouter initialEntries={['/sign-in']}>
        <SignInPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Terms' })).toHaveAttribute('href', '/terms')
    expect(screen.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password')
    expect(screen.getByRole('button', { name: 'Continue with Google' })).toBeDisabled()
    expect(screen.queryByText('3 new')).not.toBeInTheDocument()
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
