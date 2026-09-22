declare const __CHANTER_RELEASE__: string

let capture: (error: unknown) => void = () => {}

export function reportBrowserError(error: unknown) {
  capture(error)
}

/** Optional monitoring never delays rendering or needs an authenticated session. */
export async function startBrowserErrors() {
  if (!import.meta.env.PROD) return
  try {
    const response = await fetch('/operational-config.json', {
      credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(1500),
    })
    if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) return
    const text = await response.text()
    if (text.length > 1024) return
    const value = JSON.parse(text)
    if (!value || typeof value.dsn !== 'string' || !value.dsn) return
    const { installBrowserReporter } = await import('./browser-errors-client')
    capture = await installBrowserReporter(value, __CHANTER_RELEASE__) ?? capture
  } catch { /* Missing settings and unreachable receivers do not break product startup. */ }
}
