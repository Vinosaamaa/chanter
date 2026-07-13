import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { V2TopBar } from './V2TopBar'

describe('V2TopBar', () => {
  it('focuses route-scoped search with Command-F', () => {
    render(
      <MemoryRouter initialEntries={['/app/inbox']}>
        <V2TopBar onOpenMenu={vi.fn()} />
      </MemoryRouter>,
    )

    const search = screen.getByRole('searchbox', { name: /search inbox/i })
    expect(screen.getByText('⌘F')).toBeInTheDocument()

    fireEvent.keyDown(window, { key: 'f', metaKey: true })

    expect(search).toHaveFocus()
  })
})
