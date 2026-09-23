import { useEffect, useRef, useState } from 'react'
import { useAuthStore } from '../../stores/auth-store'
import type { SourceDeletionAccepted } from './source-deletion-api'
import './source-deletion-dialog.css'

type Props = {
  kind: 'STUDY_SERVER' | 'RESOURCE'
  targetId: string
  targetName: string
  submit: (signal: AbortSignal) => Promise<SourceDeletionAccepted>
  onClose: () => void
  onAccepted: (request: SourceDeletionAccepted) => void
}
export function SourceDeletionDialog(props: Props) {
  const account = useAuthStore(state => state.user?.id)
  const generation = useAuthStore(state => state.generation)
  const [openedFor] = useState({ account, generation })
  const current = Boolean(account) && account === openedFor.account && generation === openedFor.generation
  const { onClose } = props
  useEffect(() => { if (!current) onClose() }, [current, onClose])
  return current ? <DeletionConfirmation key={props.targetId} {...props} account={account!} generation={generation} /> : null
}

function DeletionConfirmation({ kind, targetId, targetName, submit, onClose, onAccepted, account, generation }: Props & { account: string; generation: number }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const active = useRef(true)
  const pending = useRef(false)
  const controller = useRef<AbortController | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    active.current = true
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const element = dialog.current
    element?.showModal()
    element?.querySelector<HTMLButtonElement>('button')?.focus()
    return () => { active.current = false; controller.current?.abort(); element?.close(); if (previous?.isConnected) previous.focus() }
  }, [])
  const current = () => active.current && useAuthStore.getState().user?.id === account && useAuthStore.getState().generation === generation
  async function confirm() {
    if (pending.current || !current()) return
    pending.current = true; setBusy(true); setError('')
    const abort = new AbortController(); controller.current = abort
    try {
      const result = await submit(abort.signal)
      if (!current()) return
      if (result.state !== 'PENDING' || result.targetId !== targetId || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(result.jobId)) throw new Error('Unverified deletion response')
      onAccepted(result)
    } catch {
      if (current()) setError('We could not verify this request. Retry deletion for this same item to recover its status. An interrupted response does not mean deletion failed or completed.')
    } finally { pending.current = false; if (current()) setBusy(false) }
  }
  return <dialog ref={dialog} className="workspace-dialog source-deletion-dialog" aria-label={`Delete ${targetName}?`} onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>
    <div className="create-event-modal">
    <h2>Delete {targetName}?</h2>
    <p>Access closes immediately and cleanup cannot be undone. {kind === 'STUDY_SERVER' ? 'This includes the Study Server, its courses, channels and enrollments.' : 'This removes the course file and starts cleanup of its associated content.'}</p>
    <p className="mt-4">Cleanup may take time. You can check progress after the request is accepted. Restricted moderation records may remain.</p>
    {error ? <p role="alert">{error}</p> : null}
    <footer className="flex-wrap"><button type="button" className="v2-outline-button" disabled={busy} onClick={onClose}>Cancel</button>
      <button type="button" className="source-deletion-confirm rounded-md px-4" disabled={busy} onClick={() => void confirm()}>{busy ? 'Requesting deletion…' : kind === 'STUDY_SERVER' ? 'Delete Study Server' : 'Delete course file'}</button></footer>
    </div>
  </dialog>
}
