const STORAGE_KEY = 'chanter-last-active-server-id'

function readSessionStorage(key: string): string | null {
  try {
    return sessionStorage.getItem(key)
  } catch {
    return null
  }
}

function writeSessionStorage(key: string, value: string) {
  try {
    sessionStorage.setItem(key, value)
  } catch {
    // Ignore blocked or unavailable storage (private mode, SSR).
  }
}

export function rememberActiveStudyServerId(serverId: string | undefined) {
  if (!serverId) {
    return
  }
  writeSessionStorage(STORAGE_KEY, serverId)
}

export function readActiveStudyServerId(): string | null {
  return readSessionStorage(STORAGE_KEY)
}
