import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { checkNativeConnection, fetchNativeConfiguration, issueNativePairing } from '../../../questions/native-companion-api'
import { NativeAnswerControls } from './NativeAnswerControls'

vi.mock('../../../questions/native-companion-api', async (original) => ({
  ...await original<typeof import('../../../questions/native-companion-api')>(),
  checkNativeConnection: vi.fn(), fetchNativeConfiguration: vi.fn(), issueNativePairing: vi.fn(),
}))
describe('native connection controls', () => {
  beforeEach(() => { vi.resetAllMocks(); vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla Windows NT 10.0') })
  afterEach(() => { cleanup(); vi.restoreAllMocks() })
  it('does no background discovery and shows truthful unavailable desktop state', async () => {
    vi.mocked(fetchNativeConfiguration).mockResolvedValue({ available: false, models: [], origin: '', publicKey: null })
    render(<NativeAnswerControls disabled={false} onInvoke={vi.fn()} />)
    expect(fetchNativeConfiguration).not.toHaveBeenCalled()
    await userEvent.click(screen.getByText('Connect my Codex account on this computer'))
    await userEvent.click(screen.getByRole('button', { name: 'Check native availability' }))
    expect(await screen.findByText(/Native access is unavailable on this deployment/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Find quotations/ })).not.toBeInTheDocument()
  })
  it('requires terminal pairing, verified connection and explicit course-export consent', async () => {
    const user = userEvent.setup(), onInvoke = vi.fn()
    const installationId = 'c17a50e8-6a31-41d7-9fe4-ec34e85f9535'
    const pair = { handle: 'x'.repeat(43), expiresAt: Date.now() + 200_000 }
    vi.mocked(fetchNativeConfiguration).mockResolvedValue({ available: true, models: ['allowed'], origin: window.location.origin, publicKey: 'public' })
    vi.mocked(issueNativePairing).mockResolvedValue({ ticket: 'signed-ticket', expiresAt: Date.now() + 120_000 })
    vi.mocked(checkNativeConnection).mockResolvedValue(['allowed'])
    render(<NativeAnswerControls disabled={false} onInvoke={onInvoke} />)
    await user.click(screen.getByText('Connect my Codex account on this computer'))
    await user.click(screen.getByRole('button', { name: 'Check native availability' }))
    await user.type(await screen.findByLabelText('Companion installation ID'), installationId)
    await user.click(screen.getByRole('button', { name: 'Create pairing command' }))
    expect(await screen.findByLabelText('Terminal pairing command')).toHaveValue('pair signed-ticket')
    expect(screen.getByText(/type the approval challenge there/)).toBeInTheDocument()
    await user.click(screen.getByLabelText('Pairing result from the terminal')); await user.paste(JSON.stringify(pair))
    await user.click(screen.getByRole('button', { name: 'Check connection' }))
    const invoke = await screen.findByRole('button', { name: 'Find quotations with my Codex account' })
    expect(invoke).toBeDisabled(); expect(onInvoke).not.toHaveBeenCalled()
    await user.click(screen.getByRole('checkbox')); await user.click(invoke)
    expect(onInvoke).toHaveBeenCalledExactlyOnceWith({ installationId, ...pair }, 'allowed')
  })
  it('offers web options on mobile without dead native buttons', () => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Android Mobile')
    render(<NativeAnswerControls disabled={false} onInvoke={vi.fn()} />)
    expect(screen.getByText(/Use the web answer options on this device/)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
