import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { MARKETING_CREATE_SERVER_PATH } from '../marketing-routes'

import { LandingPage } from './LandingPage'

describe('LandingPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    useAuthStore.getState().clearSession()
  })

  it('renders primary CTAs for visitors', () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )

    const createServerLinks = screen.getAllByRole('link', { name: /create study server/i })
    expect(createServerLinks[0]).toHaveAttribute('href', '/sign-in')
    expect(screen.getByRole('link', { name: /view demo/i })).toHaveAttribute('href', '/dev/demo')
    expect(screen.getAllByRole('link', { name: /sign in/i }).length).toBeGreaterThan(0)
  })

  it('routes authenticated users directly to onboarding', () => {
    useAuthStore.getState().setSession({
      accessToken: 'token',
      refreshToken: 'refresh',
      expiresInSeconds: 900,
      user: { id: 'user-1', email: 'teacher@example.com', displayName: 'Teacher' },
    })

    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )

    const createServerLinks = screen.getAllByRole('link', { name: /create study server/i })
    expect(createServerLinks[0]).toHaveAttribute('href', MARKETING_CREATE_SERVER_PATH)
    const getStartedLinks = screen.getAllByRole('link', { name: /get started/i })
    expect(getStartedLinks[0]).toHaveAttribute('href', MARKETING_CREATE_SERVER_PATH)
  })

  it('exposes in-page section anchors for navigation', () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )

    expect(document.getElementById('features')).toBeTruthy()
    expect(document.getElementById('use-cases')).toBeTruthy()
    expect(document.getElementById('pricing')).toBeTruthy()
  })

  it('shows product preview chrome with Join Queue and legal links', () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )

    expect(screen.getByLabelText(/product preview/i)).toBeTruthy()
    expect(screen.getByLabelText(/ta queue preview/i)).toBeTruthy()
    expect(screen.getByLabelText(/course stats/i)).toBeTruthy()
    expect(screen.getByRole('link', { name: /join queue/i })).toHaveAttribute('href', '/sign-in')
    expect(screen.getByRole('link', { name: /^terms$/i })).toHaveAttribute('href', '/terms')
    expect(screen.getByRole('link', { name: /^privacy$/i })).toHaveAttribute('href', '/privacy')
  })
})
