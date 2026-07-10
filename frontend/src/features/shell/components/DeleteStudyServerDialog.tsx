import { useEffect, useRef } from 'react'

import { Button } from '../../../components/ui/button'

function focusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(
    container.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  )
}

type DeleteStudyServerDialogProps = {
  serverName: string
  deleteError: string | null
  isDeleting: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function DeleteStudyServerDialog({
  serverName,
  deleteError,
  isDeleting,
  onCancel,
  onConfirm,
}: DeleteStudyServerDialogProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const previouslyFocusedRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    previouslyFocusedRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null

    const focusable = panelRef.current ? focusableElements(panelRef.current) : []
    focusable[0]?.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCancel()
        return
      }

      if (event.key !== 'Tab' || !panelRef.current) {
        return
      }

      const trapped = focusableElements(panelRef.current)
      if (trapped.length === 0) {
        return
      }

      const first = trapped[0]
      const last = trapped[trapped.length - 1]
      const active = document.activeElement

      if (event.shiftKey && active === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)

    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previouslyFocusedRef.current?.focus()
    }
  }, [onCancel])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <button
        type="button"
        aria-label="Close delete confirmation"
        className="absolute inset-0"
        onClick={onCancel}
        disabled={isDeleting}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="delete-study-server-title"
        className="relative z-10 w-full max-w-md rounded-xl border border-app-border bg-app-elevated p-5 shadow-xl"
      >
        <h2 id="delete-study-server-title" className="text-lg font-semibold text-app-text">
          Delete {serverName}?
        </h2>
        <p className="mt-2 text-sm text-app-muted">
          This permanently removes the Study Server, its courses, channels, and enrollments.
          This action cannot be undone.
        </p>
        {deleteError ? (
          <p role="alert" className="mt-3 text-sm text-red-300">
            {deleteError}
          </p>
        ) : null}
        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button type="button" variant="secondary" disabled={isDeleting} onClick={onCancel}>
            Cancel
          </Button>
          <Button
            type="button"
            className="bg-red-600 hover:bg-red-500"
            disabled={isDeleting}
            onClick={onConfirm}
          >
            {isDeleting ? 'Deleting…' : 'Delete Study Server'}
          </Button>
        </div>
      </div>
    </div>
  )
}
