type V2AvatarProps = {
  name: string
  tone?: 'blue' | 'green' | 'purple' | 'amber' | 'rose'
  size?: 'sm' | 'md' | 'lg'
  online?: boolean
}

export function V2Avatar({ name, tone = 'blue', size = 'md', online }: V2AvatarProps) {
  const initials = name
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  return (
    <span className={`v2-avatar ${tone} ${size}`} aria-label={name}>
      {initials}
      {online !== undefined ? <i className={online ? 'online' : undefined} /> : null}
    </span>
  )
}
