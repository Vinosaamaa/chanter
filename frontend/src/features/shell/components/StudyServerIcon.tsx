import { studyServerIconForeground, studyServerIconStyle } from '../study-server-icon-style'

type StudyServerIconProps = {
  serverId: string
  active?: boolean
  size?: 'sm' | 'md'
}

function StudyServerGlyph({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill="currentColor"
      aria-hidden
    >
      <path d="M12 3 2 8.25 12 13.5l7.5-3.75V16h2V7.5L12 3Zm0 13.5L4.5 12v5.25L12 21l7.5-3.75V12L12 16.5Z" />
    </svg>
  )
}

export function StudyServerIcon({ serverId, active = false, size = 'md' }: StudyServerIconProps) {
  const { color } = studyServerIconStyle(serverId)
  const foreground = studyServerIconForeground(color, active)
  const dimension = size === 'sm' ? 'h-10 w-10' : 'h-12 w-12'
  const iconSize = size === 'sm' ? 'h-5 w-5' : 'h-6 w-6'

  return (
    <span
      className={`inline-flex ${dimension} items-center justify-center rounded-2xl transition-transform`}
      style={{
        backgroundColor: active ? color : `${color}40`,
        color: foreground,
        boxShadow: active ? `0 0 0 2px ${color}` : undefined,
      }}
      aria-hidden
    >
      <StudyServerGlyph className={iconSize} />
    </span>
  )
}
