import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../auth-api'
import { HumanVerificationControl } from './HumanVerificationControl'

afterEach(() => { cleanup(); delete window.turnstile; vi.restoreAllMocks() })

describe('optional human verification', () => {
  it('leaves disabled deployments usable without loading a challenge', async () => {
    vi.spyOn(authApi, 'fetchVerificationOptions').mockResolvedValue({ enabled: false, siteKey: null })
    const changed = vi.fn()
    render(<HumanVerificationControl action="register" onChange={changed} />)
    await waitFor(() => expect(changed).toHaveBeenCalledWith({}))
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(document.querySelector('script[src*="challenges.cloudflare.com"]')).toBeNull()
  })

  it('expires tokens and ignores late callbacks after switching to email', async () => {
    vi.spyOn(authApi, 'fetchVerificationOptions').mockResolvedValue({ enabled: true, siteKey: 'public-key' })
    const widget = { render: vi.fn(() => 'widget'), remove: vi.fn() }
    window.turnstile = widget
    const changed = vi.fn()
    render(<HumanVerificationControl action="register" onChange={changed} />)
    await waitFor(() => expect(widget.render).toHaveBeenCalledOnce())
    const options = (widget.render.mock.calls[0] as unknown as Parameters<NonNullable<Window['turnstile']>['render']>)[1]
    act(() => options.callback('single-use-token'))
    expect(changed).toHaveBeenLastCalledWith({ token: 'single-use-token' })
    act(() => options['expired-callback']())
    expect(changed).toHaveBeenLastCalledWith(null)
    await userEvent.click(screen.getByRole('button', { name: 'Use email verification instead' }))
    expect(changed).toHaveBeenLastCalledWith({ method: 'email' })
    expect(widget.remove).toHaveBeenCalledWith('widget')
    act(() => options.callback('late-token'))
    expect(changed).toHaveBeenLastCalledWith({ method: 'email' })
    expect(screen.getByRole('status')).toHaveTextContent('link sent to your email')
  })

  it('keeps an explicit email route when options cannot load', async () => {
    vi.spyOn(authApi, 'fetchVerificationOptions').mockRejectedValue(new Error('offline'))
    const changed = vi.fn()
    render(<HumanVerificationControl action="recovery" onChange={changed} />)
    await screen.findByText('Verification could not load. You can continue by email.')
    expect(changed).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Use email verification instead' }))
    expect(changed).toHaveBeenLastCalledWith({ method: 'email' })
  })

  it('removes the widget on unmount and does not expose its late token', async () => {
    vi.spyOn(authApi, 'fetchVerificationOptions').mockResolvedValue({ enabled: true, siteKey: 'public-key' })
    const widget = { render: vi.fn(() => 'widget'), remove: vi.fn() }
    window.turnstile = widget
    const changed = vi.fn()
    const page = render(<HumanVerificationControl action="recovery" onChange={changed} />)
    await waitFor(() => expect(widget.render).toHaveBeenCalledOnce())
    const options = (widget.render.mock.calls[0] as unknown as Parameters<NonNullable<Window['turnstile']>['render']>)[1]
    page.unmount()
    act(() => options.callback('late-token'))
    expect(widget.remove).toHaveBeenCalledWith('widget')
    expect(changed).not.toHaveBeenCalled()
  })
})
