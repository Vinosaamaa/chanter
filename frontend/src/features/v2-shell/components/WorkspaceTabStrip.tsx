import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'

/** Visible scroll controls make clipped tabs discoverable on touch and narrow windows. */
export function WorkspaceTabStrip({ children, className, label }: { children: ReactNode; className: string; label: string }) {
  const list = useRef<HTMLElement>(null)
  const [position, setPosition] = useState({ overflow: false, start: true, end: false })
  useEffect(() => {
    const element = list.current
    if (!element) return
    const update = () => setPosition({
      overflow: element.scrollWidth > element.clientWidth + 1,
      start: element.scrollLeft < 1,
      end: element.scrollLeft + element.clientWidth >= element.scrollWidth - 1,
    })
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(update)
    observer?.observe(element)
    window.addEventListener('resize', update)
    element.addEventListener('scroll', update, { passive: true })
    update()
    return () => { observer?.disconnect(); element.removeEventListener('scroll', update); window.removeEventListener('resize', update) }
  }, [])
  useEffect(() => {
    list.current?.querySelector<HTMLElement>('[aria-current="page"]')?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
  }, [children])
  const scroll = (direction: number) => {
    list.current?.scrollBy({ left: direction * list.current.clientWidth * .75, behavior: 'auto' })
  }
  return <div className="workspace-tab-strip">
    {position.overflow ? <button type="button" className="tab-scroll-control" aria-label={`Previous ${label.toLowerCase()}`} disabled={position.start} onClick={() => scroll(-1)}><ChevronLeft size={18} /></button> : null}
    <nav className={className} aria-label={label} ref={list}>{children}</nav>
    {position.overflow ? <button type="button" className="tab-scroll-control" aria-label={`More ${label.toLowerCase()}`} disabled={position.end} onClick={() => scroll(1)}><ChevronRight size={18} /></button> : null}
  </div>
}
