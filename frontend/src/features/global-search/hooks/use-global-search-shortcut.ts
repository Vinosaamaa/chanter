import { useEffect } from 'react'

type UseGlobalSearchShortcutOptions = {
  isOpen: boolean
  onOpen: () => void
  onClose: () => void
}

export function useGlobalSearchShortcut({
  isOpen,
  onOpen,
  onClose,
}: UseGlobalSearchShortcutOptions) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target
      const isEditable =
        target instanceof HTMLInputElement
        || target instanceof HTMLTextAreaElement
        || (target instanceof HTMLElement && target.isContentEditable)

      if (
        (event.metaKey || event.ctrlKey)
        && ['f', 'k'].includes(event.key.toLowerCase())
      ) {
        event.preventDefault()
        if (!isOpen) {
          onOpen()
        }
        return
      }

      if (event.key === 'Escape' && isOpen) {
        event.preventDefault()
        onClose()
        return
      }

      if (isOpen || isEditable) {
        return
      }

      if (event.key === '/') {
        event.preventDefault()
        onOpen()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [isOpen, onClose, onOpen])
}
