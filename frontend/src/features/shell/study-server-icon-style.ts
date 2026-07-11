const SERVER_ICON_COLORS = [
  '#5865f2',
  '#57f287',
  '#fee75c',
  '#eb459e',
  '#ed4245',
  '#00b0f4',
  '#faa61a',
  '#9b59b6',
] as const

function hashString(value: string): number {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0
  }
  return hash
}

export function studyServerIconStyle(serverId: string): { color: string } {
  const hash = hashString(serverId)
  return {
    color: SERVER_ICON_COLORS[hash % SERVER_ICON_COLORS.length],
  }
}

export function studyServerIconForeground(backgroundColor: string, active: boolean): string {
  if (!active) {
    return backgroundColor
  }

  const hex = backgroundColor.replace('#', '')
  if (hex.length !== 6) {
    return '#ffffff'
  }

  const red = Number.parseInt(hex.slice(0, 2), 16)
  const green = Number.parseInt(hex.slice(2, 4), 16)
  const blue = Number.parseInt(hex.slice(4, 6), 16)
  const luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
  return luminance > 0.62 ? '#111827' : '#ffffff'
}
