import { BrowserClient, defaultStackParser, makeFetchTransport } from '@sentry/browser'
import type { ErrorEvent, StackFrame } from '@sentry/browser'

type Configuration = { dsn: string; release: string; environment: string }
const releasePattern = /^[a-f0-9]{40}$/
const errorTypes = new Set(['Error', 'TypeError', 'RangeError', 'ReferenceError', 'SyntaxError', 'URIError', 'EvalError', 'AggregateError'])
const compiledAssetPattern = /^\/assets\/[A-Za-z0-9_-]{1,180}-[A-Za-z0-9_-]{8,}\.js$/

/** Release validation and event hooks load only after an operator enables reporting. */
export async function installBrowserReporter(value: unknown, release: string) {
  const config = readBrowserErrorConfiguration(value, release)
  if (!config) return null
  const response = await fetch('/browser-error-assets.json', {
    credentials: 'omit', cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(1500),
  })
  if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) return null
  const text = await response.text()
  if (text.length > 65_536) return null
  const assets = readBrowserErrorAssets(JSON.parse(text), config.release)
  if (!assets) return null
  const reporter = createBrowserReporter(config, window.location.origin, assets)
  window.addEventListener('error', event => reporter.capture(event.error))
  window.addEventListener('unhandledrejection', event => reporter.capture(event.reason))
  return reporter.capture
}

export function readBrowserErrorAssets(value: unknown, release: string): ReadonlySet<string> | null {
  if (!value || typeof value !== 'object') return null
  const manifest = value as { release?: unknown; assets?: unknown }
  if (manifest.release !== release || !Array.isArray(manifest.assets) || manifest.assets.length < 1 || manifest.assets.length > 500
    || !manifest.assets.every(asset => typeof asset === 'string' && asset.length <= 191 && compiledAssetPattern.test(asset))) return null
  return new Set<string>(manifest.assets)
}

export function readBrowserErrorConfiguration(value: unknown, release: string): Configuration | null {
  try {
    if (!value || typeof value !== 'object' || !releasePattern.test(release)) return null
    const config = value as Partial<Configuration>
    if (config.release !== release || !['production', 'staging'].includes(config.environment ?? '') || typeof config.dsn !== 'string') return null
    const dsn = new URL(config.dsn)
    if (dsn.protocol !== 'https:' || !dsn.hostname.endsWith('.sentry.io') || !/^[a-f0-9]{32}$/.test(dsn.username)
      || dsn.password || !/^\/[0-9]{1,20}$/.test(dsn.pathname) || dsn.search || dsn.hash || dsn.port || /[\s\0]/.test(config.dsn)) return null
    return { dsn: dsn.href, release, environment: config.environment! }
  } catch { return null }
}

function safeFrames(frames: StackFrame[], origin: string, assets: ReadonlySet<string>): StackFrame[] {
  const accepted: StackFrame[] = []
  // Stack parsers return oldest first. Retain the nearest twelve application frames.
  for (const frame of frames.slice(-64).reverse()) {
    if (typeof frame.filename !== 'string' || frame.filename.length > 400) continue
    try {
      const url = new URL(frame.filename)
      if (url.origin !== origin || url.username || url.password || url.search || url.hash || !assets.has(url.pathname)
        || !compiledAssetPattern.test(url.pathname)
        || !Number.isSafeInteger(frame.lineno) || frame.lineno! < 1 || frame.lineno! > 10_000_000
        || !Number.isSafeInteger(frame.colno) || frame.colno! < 1 || frame.colno! > 10_000_000) continue
      accepted.push({ filename: `${origin}${url.pathname}`, lineno: frame.lineno, colno: frame.colno, in_app: true })
      if (accepted.length === 12) break
    } catch { /* Foreign or malformed locations carry no useful application evidence. */ }
  }
  return accepted.reverse()
}

function safeEvent(event: Pick<ErrorEvent, 'exception' | 'event_id'>, config: Configuration, origin: string, assets: ReadonlySet<string>): ErrorEvent | null {
  const exception = event.exception?.values?.[0]
  const frames = safeFrames(exception?.stacktrace?.frames ?? [], origin, assets)
  if (!exception || frames.length === 0) return null
  return {
    ...(typeof event.event_id === 'string' && /^[a-f0-9]{32}$/.test(event.event_id) ? { event_id: event.event_id } : {}),
    type: undefined, level: 'error', platform: 'javascript', release: config.release, environment: config.environment,
    exception: { values: [{ type: errorTypes.has(exception.type ?? '') ? exception.type : 'Error', stacktrace: { frames } }] },
  }
}

/** Direct client: never hand the SDK an original Error, request, scope or user. */
export function createBrowserReporter(config: Configuration, origin: string, assets: ReadonlySet<string>, send: typeof fetch = fetch, now: () => number = () => performance.now()) {
  if (!readBrowserErrorConfiguration(config, config.release) || new URL(origin).origin !== origin) throw new Error('Invalid browser error configuration')
  const client = new BrowserClient({
    dsn: config.dsn, release: config.release, environment: config.environment,
    integrations: [], stackParser: defaultStackParser, sendClientReports: false,
    attachStacktrace: false, maxBreadcrumbs: 0, tracesSampleRate: 0,
    enableLogs: false, enableMetrics: false,
    dataCollection: { userInfo: false, cookies: false, httpHeaders: false, httpBodies: [], urlQueryParams: false,
      graphQL: { document: false, variables: false }, genAI: { inputs: false, outputs: false }, databaseQueryData: false },
    transport: options => makeFetchTransport({ ...options, bufferSize: 5 }, (input, init) => send(input, {
      ...init, credentials: 'omit', referrerPolicy: 'no-referrer', redirect: 'error', keepalive: false,
      signal: AbortSignal.timeout(1500),
    })),
    beforeSend: event => safeEvent(event, config, origin, assets),
  })
  client.init()
  let windowStart = now()
  let count = 0
  let closed = false
  return {
    capture(error: unknown) {
      try {
        if (closed || !(error instanceof Error) || typeof error.stack !== 'string') return
        const time = now()
        if (time - windowStart >= 60_000) { windowStart = time; count = 0 }
        if (count >= 5) return
        // Slice before parsing: arbitrarily long user-controlled error text cannot grow work.
        const event = safeEvent({ exception: { values: [{ type: error.name,
          stacktrace: { frames: defaultStackParser(error.stack.slice(0, 16_384)) } }] } }, config, origin, assets)
        if (!event) return
        count++
        client.captureEvent(event)
      } catch { /* Reporting cannot change the product's error behavior. */ }
    },
    flush: () => client.flush(1500),
    close: () => { closed = true; return client.close(1500) },
  }
}
