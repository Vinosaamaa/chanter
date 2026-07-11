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
