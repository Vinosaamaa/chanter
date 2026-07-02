import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { LandingPage } from './LandingPage'

describe('LandingPage', () => {
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
})
