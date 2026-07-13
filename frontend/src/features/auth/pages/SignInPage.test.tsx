import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { SignInPage } from './SignInPage'

describe('SignInPage public destinations', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, refreshToken: null, user: null })
  })

  it('exposes Terms and marks unavailable Google sign-in as disabled', () => {
    render(
      <MemoryRouter initialEntries={['/sign-in']}>
        <SignInPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Terms' })).toHaveAttribute('href', '/terms')
    expect(screen.getByRole('button', { name: 'Continue with Google' })).toBeDisabled()
    expect(screen.queryByText('3 new')).not.toBeInTheDocument()
  })
})
