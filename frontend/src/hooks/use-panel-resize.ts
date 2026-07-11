import { useCallback, type PointerEvent as ReactPointerEvent } from 'react'

export function usePanelResize({
  side,
  onWidthChange,
  minWidth,
  maxWidth,
}: {
  side: 'left' | 'right'
  onWidthChange: (width: number) => void
  minWidth: number
  maxWidth: number
}) {
  const onPointerDown = useCallback(
    (event: ReactPointerEvent<HTMLDivElement>) => {
      event.preventDefault()
      const panel = event.currentTarget.parentElement as HTMLElement
      const startX = event.clientX
      const startWidth = panel.getBoundingClientRect().width

      const onPointerMove = (moveEvent: PointerEvent) => {
        const delta =
          side === 'left' ? moveEvent.clientX - startX : startX - moveEvent.clientX
        const nextWidth = Math.min(maxWidth, Math.max(minWidth, startWidth + delta))
        onWidthChange(nextWidth)
      }

      const onPointerUp = () => {
        window.removeEventListener('pointermove', onPointerMove)
        window.removeEventListener('pointerup', onPointerUp)
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
      }

      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
      window.addEventListener('pointermove', onPointerMove)
      window.addEventListener('pointerup', onPointerUp)
    },
    [maxWidth, minWidth, onWidthChange, side],
  )

  return { onPointerDown }
}
