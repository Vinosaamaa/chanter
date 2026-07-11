const STORAGE_KEY = 'chanter-last-active-server-id'

export function rememberActiveStudyServerId(serverId: string | undefined) {
  if (!serverId) {
    return
  }
  sessionStorage.setItem(STORAGE_KEY, serverId)
}

export function readActiveStudyServerId(): string | null {
  return sessionStorage.getItem(STORAGE_KEY)
}
