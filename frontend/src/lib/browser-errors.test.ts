import { describe, expect, it } from 'vitest'
import { getGlobalScope } from '@sentry/browser'
import { createBrowserReporter, readBrowserErrorConfiguration } from './browser-errors-client'

const release = 'a'.repeat(40)
const origin = 'https://chanter.example'
const config = { dsn: `https://${'b'.repeat(32)}@o1.ingest.us.sentry.io/1`, release, environment: 'production' }
const assets = new Set(['/assets/index-AbC_1234.js', '/assets/route-xYz_1234.js'])

describe('private browser errors', () => {
  it('requires an explicit receiver and the exact compiled release', () => {
    expect(readBrowserErrorConfiguration({}, release)).toBeNull()
    expect(readBrowserErrorConfiguration({ ...config, release: 'c'.repeat(40) }, release)).toBeNull()
    expect(readBrowserErrorConfiguration({ ...config, dsn: config.dsn + '?private-canary' }, release)).toBeNull()
    expect(readBrowserErrorConfiguration({ ...config, dsn: config.dsn.replace('https:', 'http:') }, release)).toBeNull()
    expect(readBrowserErrorConfiguration({ ...config, environment: 'private-canary' }, release)).toBeNull()
    expect(readBrowserErrorConfiguration(config, release)).toEqual(config)
  })

  it('sends a real SDK envelope containing only bounded compiled code locations', async () => {
    const requests: { url: string; options: RequestInit }[] = []
    const reporter = createBrowserReporter(config, origin, assets, async (input, options) => {
      requests.push({ url: String(input), options: options! })
      return new Response('{}', { status: 200 })
    })
    const error = new TypeError('private-canary message')
    error.stack = `TypeError: private-canary\n    at privateCanary (${origin}/assets/index-AbC_1234.js:20:9)\n` +
      `    at secret (${origin}/api/accounts/private-canary:1:1)\n` +
      `    at secret (https://private-canary.example/assets/index-AbC_1234.js:1:1)\n` +
      `    at secret (${origin}/assets/index-AbC_1234.js?private-canary:1:1)\n` +
      `    at secret (${origin}/assets/private-canary-AbC_1234.js:1:1)\n` +
      Array.from({ length: 30 }, (_, i) => `    at privateCanary (${origin}/assets/route-xYz_1234.js:${30 + i}:4)`).join('\n')
    const scope = getGlobalScope()
    scope.setUser({ id: 'private-canary', email: 'private-canary@example.test' })
    scope.setContext('private-canary', { body: 'private-canary' })
    scope.addBreadcrumb({ message: 'private-canary' })
    try {
      reporter.capture(error)
      await reporter.close()
    } finally { scope.clear() }
    expect(requests).toHaveLength(1)
    const request = requests[0]
    expect(request.options.credentials).toBe('omit')
    expect(request.options.referrerPolicy).toBe('no-referrer')
    expect(request.options.redirect).toBe('error')
    expect(request.options.keepalive).toBe(false)
    expect(request.options.signal).toBeInstanceOf(AbortSignal)
    const body = String(request.options.body)
    expect(body).not.toMatch(/private.canary|breadcrumbs|request|contexts|user_agent|"user"|"message"|"function"/i)
    const event = JSON.parse(body.trim().split('\n').at(-1)!)
    expect(event.release).toBe(release)
    expect(event.exception.values[0].type).toBe('TypeError')
    expect(event.exception.values[0].stacktrace.frames).toHaveLength(12)
    expect(event.exception.values[0].stacktrace.frames.at(-1)).toEqual({ filename: `${origin}/assets/index-AbC_1234.js`, lineno: 20, colno: 9, in_app: true })
  })

  it('drops non-errors and extension frames and bounds overload even on receiver failure', async () => {
    let requests = 0
    let now = 0
    const reporter = createBrowserReporter(config, origin, assets, async () => { requests++; throw new Error('private-canary outage') }, () => now)
    reporter.capture('private-canary')
    reporter.capture(new Error('no compiled application frames'))
    const error = new Error('private-canary')
    error.stack = `privateFunction@${origin}/assets/index-AbC_1234.js:1:2`
    for (let i = 0; i < 200; i++) reporter.capture(error)
    await reporter.flush()
    expect(requests).toBe(5)
    now = 60_001
    reporter.capture(error)
    await reporter.close()
    expect(requests).toBe(6)
  })
})
