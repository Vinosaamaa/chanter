import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import { SourceDeletionDialog } from './SourceDeletionDialog'

const accepted = { jobId: 'b633c892-6762-40ec-a945-b042957a052b', targetId: 'resource-1', state: 'PENDING' as const }
const submit = vi.fn(), close = vi.fn(), onAccepted = vi.fn()
function session(id = 'owner') { useAuthStore.getState().setSession({ accessToken: id, expiresInSeconds: 900, user: { id, email: `${id}@example.test`, displayName: id } }) }
function open() { render(<SourceDeletionDialog kind="RESOURCE" targetId="resource-1" targetName="Week one notes" submit={submit} onClose={close} onAccepted={onAccepted} />) }
beforeEach(() => {
  vi.resetAllMocks(); session()
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function (this: HTMLDialogElement) { this.open = true } })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function (this: HTMLDialogElement) { this.open = false } })
})
afterEach(cleanup)

it('requires confirmation, prevents duplicate submission and returns the pending receipt', async () => {
  let resolve!: (value: typeof accepted) => void
  submit.mockReturnValue(new Promise(done => { resolve = done }))
  open()
  expect(submit).not.toHaveBeenCalled()
  expect(screen.getByText(/Access closes immediately/)).toBeVisible()
  await userEvent.setup().click(screen.getByRole('button', { name: 'Delete course file' }))
  expect(screen.getByRole('button', { name: 'Requesting deletion…' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  await act(async () => resolve(accepted))
  expect(onAccepted).toHaveBeenCalledWith(accepted)
  expect(screen.queryByText('Deletion completed')).not.toBeInTheDocument()
})

it('aborts and ignores an accepted response from an old account', async () => {
  let resolve!: (value: typeof accepted) => void
  submit.mockReturnValue(new Promise(done => { resolve = done }))
  open()
  await userEvent.setup().click(screen.getByRole('button', { name: 'Delete course file' }))
  const signal = submit.mock.calls[0][0] as AbortSignal
  await act(async () => session('other'))
  await act(async () => resolve(accepted))
  expect(signal.aborted).toBe(true)
  expect(onAccepted).not.toHaveBeenCalled()
})

it('retains an uncertain request for explicit retry without claiming success', async () => {
  submit.mockRejectedValueOnce(new Error('network lost')).mockResolvedValue(accepted)
  open()
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: 'Delete course file' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('could not verify')
  expect(onAccepted).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Delete course file' }))
  expect(onAccepted).toHaveBeenCalledWith(accepted)
  expect(submit).toHaveBeenCalledTimes(2)
})
