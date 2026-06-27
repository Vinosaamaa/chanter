import { renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGlobalSearchShortcut } from './use-global-search-shortcut'

describe('useGlobalSearchShortcut', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('opens on meta+k', () => {
    const onOpen = vi.fn()
    const onClose = vi.fn()

    renderHook(() =>
      useGlobalSearchShortcut({
        isOpen: false,
        onOpen,
        onClose,
      }),
    )

    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'k', metaKey: true, bubbles: true }),
    )

    expect(onOpen).toHaveBeenCalledTimes(1)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('closes on escape when open', () => {
    const onOpen = vi.fn()
    const onClose = vi.fn()

    renderHook(() =>
      useGlobalSearchShortcut({
        isOpen: true,
        onOpen,
        onClose,
      }),
    )

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(onOpen).not.toHaveBeenCalled()
  })
})
