import { useCallback, useEffect, useRef, type PointerEvent as ReactPointerEvent } from 'react'

export function usePanelResize({
  getPanel,
  side,
  onWidthChange,
  minWidth,
  maxWidth,
}: {
  getPanel: () => HTMLElement | null
  side: 'left' | 'right'
  onWidthChange: (width: number) => void
  minWidth: number
  maxWidth: number
}) {
  const dragCleanupRef = useRef<(() => void) | null>(null)

  useEffect(() => {
    return () => {
      dragCleanupRef.current?.()
    }
  }, [])

  const onPointerDown = useCallback(
    (event: ReactPointerEvent<HTMLDivElement>) => {
      event.preventDefault()
      const panel = getPanel()
      if (!panel) {
        return
      }

      dragCleanupRef.current?.()

      const startX = event.clientX
      const startWidth = panel.getBoundingClientRect().width

      const cleanupDrag = () => {
        window.removeEventListener('pointermove', onPointerMove)
        window.removeEventListener('pointerup', onPointerUp)
        window.removeEventListener('pointercancel', onPointerUp)
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
        dragCleanupRef.current = null
      }

      const onPointerMove = (moveEvent: PointerEvent) => {
        const delta =
          side === 'left' ? moveEvent.clientX - startX : startX - moveEvent.clientX
        const nextWidth = Math.min(maxWidth, Math.max(minWidth, startWidth + delta))
        onWidthChange(nextWidth)
      }

      const onPointerUp = () => {
        cleanupDrag()
      }

      dragCleanupRef.current = cleanupDrag
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
      window.addEventListener('pointermove', onPointerMove)
      window.addEventListener('pointerup', onPointerUp)
      window.addEventListener('pointercancel', onPointerUp)
    },
    [getPanel, maxWidth, minWidth, onWidthChange, side],
  )

  const onSeparatorKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      const panel = getPanel()
      if (!panel) {
        return
      }

      const step = event.shiftKey ? 40 : 16
      const currentWidth = panel.getBoundingClientRect().width

      if (event.key === 'ArrowLeft') {
        event.preventDefault()
        const delta = side === 'left' ? -step : step
        onWidthChange(Math.min(maxWidth, Math.max(minWidth, currentWidth + delta)))
      } else if (event.key === 'ArrowRight') {
        event.preventDefault()
        const delta = side === 'left' ? step : -step
        onWidthChange(Math.min(maxWidth, Math.max(minWidth, currentWidth + delta)))
      }
    },
    [getPanel, maxWidth, minWidth, onWidthChange, side],
  )

  return { onPointerDown, onSeparatorKeyDown }
}
