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

  it('opens on meta+f for the v2 search shortcut', () => {
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
      new KeyboardEvent('keydown', { key: 'f', metaKey: true, bubbles: true }),
    )

    expect(onOpen).toHaveBeenCalledTimes(1)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('opens on ctrl+k', () => {
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
      new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true }),
    )

    expect(onOpen).toHaveBeenCalledTimes(1)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('does not close on meta+k when already open', () => {
    const onOpen = vi.fn()
    const onClose = vi.fn()

    renderHook(() =>
      useGlobalSearchShortcut({
        isOpen: true,
        onOpen,
        onClose,
      }),
    )

    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'k', metaKey: true, bubbles: true }),
    )

    expect(onOpen).not.toHaveBeenCalled()
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

  it('opens on slash when not focused in an editable field', () => {
    const onOpen = vi.fn()
    const onClose = vi.fn()

    renderHook(() =>
      useGlobalSearchShortcut({
        isOpen: false,
        onOpen,
        onClose,
      }),
    )

    document.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }))

    expect(onOpen).toHaveBeenCalledTimes(1)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('ignores slash when focused in an input', () => {
    const onOpen = vi.fn()
    const onClose = vi.fn()
    const input = document.createElement('input')
    document.body.appendChild(input)
    input.focus()

    renderHook(() =>
      useGlobalSearchShortcut({
        isOpen: false,
        onOpen,
        onClose,
      }),
    )

    input.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }))

    expect(onOpen).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()

    input.remove()
  })
})
