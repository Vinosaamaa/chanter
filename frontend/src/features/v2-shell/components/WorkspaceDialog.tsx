import { useEffect, useRef, type ReactNode } from 'react'

/** Native modality shared by community forms and event details. */
export function WorkspaceDialog({ label, onClose, children }: { label: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    dialog?.showModal()
    dialog?.querySelector<HTMLElement>('input, textarea, button')?.focus()
    return () => {
      dialog?.close()
      if (previous?.isConnected) previous.focus()
    }
  }, [])
  return <dialog ref={ref} className="workspace-dialog" aria-label={label} onCancel={onClose}>{children}</dialog>
}
