import { execFileSync } from 'node:child_process'
import { expect, test } from '@playwright/test'

const release = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim()
const dsn = `https://${'b'.repeat(32)}@o1.ingest.us.sentry.io/1`

test('optional browser reports preserve only release-bound code locations @critical', async ({ page }) => {
  const envelopes: { body: string; headers: Record<string, string> }[] = []
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ status: 204 }))
  await page.route('**/operational-config.json', route => route.fulfill({ json: { dsn, release, environment: 'staging' } }))
  await page.route('https://o1.ingest.us.sentry.io/**', async route => {
    envelopes.push({ body: route.request().postData() ?? '', headers: await route.request().allHeaders() })
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })
  const assetsReady = page.waitForResponse('**/browser-error-assets.json')
  await page.goto('/terms')
  const manifest = await (await assetsReady).json()
  expect(manifest.release).toBe(release)
  expect(manifest.assets.length).toBeGreaterThan(0)
  await expect.poll(async () => {
    if (envelopes.length === 0) await page.evaluate(asset => {
      const error = new TypeError('private-canary content')
      error.stack = `TypeError: private-canary\n    at privateCanary (${location.origin}${asset}:1:1)`
      window.dispatchEvent(new ErrorEvent('error', { error, message: 'private-canary', filename: '/accounts/private-canary' }))
    }, manifest.assets[0])
    return envelopes.length
  }).toBe(1)
  expect(envelopes[0].body).not.toMatch(/private.canary|"request"|"user"|breadcrumbs|"message"|"function"/i)
  expect(envelopes[0].body).toContain(release)
  expect(envelopes[0].headers.cookie).toBeUndefined()
  expect(envelopes[0].headers.referer).toBeUndefined()
  // Navigation still works after an operational report.
  await page.goto('/privacy')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
})

test('disabled browser reporting makes no provider request or SDK download @critical', async ({ page }) => {
  const unwanted: string[] = []
  page.on('request', request => {
    if (request.url().includes('sentry.io') || request.url().includes('/assets/browser-errors-client-')) unwanted.push(request.url())
  })
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ status: 204 }))
  await page.route('**/operational-config.json', route => route.fulfill({ json: {} }))
  await page.goto('/terms')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await page.goto('/privacy')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  expect(unwanted).toEqual([])
})
