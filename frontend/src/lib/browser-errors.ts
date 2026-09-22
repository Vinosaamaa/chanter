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
    const { readBrowserErrorConfiguration, createBrowserReporter } = await import('./browser-errors-client')
    const config = readBrowserErrorConfiguration(value, __CHANTER_RELEASE__)
    if (!config) return
    const assetResponse = await fetch('/browser-error-assets.json', {
      credentials: 'omit', cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(1500),
    })
    if (!assetResponse.ok || !assetResponse.headers.get('content-type')?.includes('application/json')) return
    const assetText = await assetResponse.text()
    if (assetText.length > 65_536) return
    const manifest = JSON.parse(assetText)
    if (manifest.release !== config.release || !Array.isArray(manifest.assets) || manifest.assets.length > 500
      || !manifest.assets.every((asset: unknown) => typeof asset === 'string' && /^\/assets\/[A-Za-z0-9_-]{1,180}\.js$/.test(asset))) return
    const reporter = createBrowserReporter(config, window.location.origin, new Set<string>(manifest.assets))
    capture = reporter.capture
    window.addEventListener('error', event => reportBrowserError(event.error))
    window.addEventListener('unhandledrejection', event => reportBrowserError(event.reason))
  } catch { /* Missing settings and unreachable receivers do not break product startup. */ }
}
