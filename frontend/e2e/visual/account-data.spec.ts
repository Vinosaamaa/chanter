import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { ExportJob } from '../../src/features/account-data/account-data-api'

const id = 'b633c892-6762-40ec-a945-b042957a052b'
const endpoint = '/api/v1/auth/account/exports'
const job: ExportJob = {
  schemaVersion: 1, id, accountId: 'visual-learner', requestedAt: '2026-09-22T18:00:00Z', expiresAt: '2026-09-23T18:00:00Z',
  state: 'READY', cleanupPending: false,
  parts: ['auth', 'community', 'message', 'media', 'agent', 'notification', 'search'].map(source => ({ source, state: 'READY', errorCode: null })),
}
// Browser download-manager compatibility and layout only. Real credential and ZIP integrity tests are backend-owned.
for (const width of [320, 390, 1280]) {
  test(`account data native download at ${width} @accountdata`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 900 })
    let grants = 0
    let downloads = 0
    await page.route(`**${endpoint}**`, async route => {
      const request = route.request()
      const url = new URL(request.url())
      if (url.pathname === endpoint) return route.fulfill({ json: [job] })
      if (url.pathname.endsWith('/download-authorization')) {
        expect(request.method()).toBe('POST')
        expect(request.headers()['x-chanter-csrf']).toBe('1')
        expect(request.headers().authorization).toBeTruthy()
        grants++
        return route.fulfill({ status: 204 })
      }
      if (url.pathname.endsWith('/download')) {
        expect(request.method()).toBe('GET')
        expect(url.search).toBe('')
        expect(request.headers().authorization).toBeUndefined()
        expect(grants).toBeGreaterThan(downloads)
        downloads++
        return route.fulfill({ contentType: 'application/zip', headers: { 'Content-Disposition': 'attachment; filename="synthetic-account.zip"', 'Cache-Control': 'no-store' },
          body: Buffer.from('UEsFBgAAAAAAAAAAAAAAAAAAAAAAAA==', 'base64') })
      }
      return route.fulfill({ status: 404 })
    })
    await page.goto('/app/account-data')
    await expect(page.getByRole('heading', { name: 'Account data', exact: true, level: 1 })).toBeVisible()
    await expect(page.getByText('Ready to download', { exact: true })).toBeVisible()
    await page.getByText('Source details', { exact: true }).click()
    await expect(page.getByText('Study Servers and memberships', { exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: info.outputPath(`fixture-ui-account-data-ready-${width}.png`), fullPage: true })
    if (width === 1280) expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([])
    const transfer = page.waitForEvent('download')
    await page.getByRole('button', { name: 'Download ZIP' }).click()
    const artifact = await transfer
    expect(artifact.suggestedFilename()).toBe('synthetic-account.zip')
    expect(await artifact.failure()).toBeNull()
    await expect(page).toHaveURL(/\/app\/account-data$/)
    await expect(page.getByText(/Download requested. Check your browser/)).toBeVisible()
    const retry = page.waitForEvent('download')
    await page.getByRole('button', { name: 'Download ZIP' }).click()
    expect(await (await retry).failure()).toBeNull()
    expect(grants).toBe(2)
    expect(downloads).toBe(2)
  })
}

for (const state of ['BUILDING', 'CANCELLED', 'EXPIRED'] as const) {
  test(`account data ${state.toLowerCase()} on phone @accountdata`, async ({ page }, info) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.route(`**${endpoint}`, route => route.fulfill({ json: [{ ...job, state, cleanupPending: state === 'CANCELLED',
      parts: job.parts.map(part => ({ ...part, state: part.source === 'auth' ? 'READY' : 'PENDING', errorCode: state === 'BUILDING' && part.source === 'media' ? 'DELIVERY_FAILED' : null })) }] }))
    await page.goto('/app/account-data')
    await expect(page.getByRole('heading', { name: 'Latest request' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Download ZIP' })).toHaveCount(0)
    if (state === 'BUILDING') await expect(page.getByText('Preparation needs attention')).toBeVisible()
    if (state === 'CANCELLED') await expect(page.getByText(/Some sources have not yet confirmed removal/)).toBeVisible()
    if (state === 'EXPIRED') await expect(page.getByText(/This export is no longer available/)).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: info.outputPath(`fixture-ui-account-data-${state.toLowerCase()}.png`), fullPage: true })
  })
}
