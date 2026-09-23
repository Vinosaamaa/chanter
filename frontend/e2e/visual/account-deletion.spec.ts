import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { DeletionJob } from '../../src/features/account-data/account-deletion-api'

const id = 'b633c892-6762-40ec-a945-b042957a052b'
const endpoint = `/api/v1/auth/account/deletions/${id}`
const prepared: DeletionJob = {
  id, state: 'PREPARED', createdAt: '2026-09-22T18:00:00Z', preparationExpiresAt: '2099-09-22T18:05:00Z', replicationPending: false, preparationError: null,
  parts: ['auth', 'community', 'message', 'media', 'agent', 'search', 'notification'].map(source => ({ source, state: 'PENDING', errorCode: null })),
}

// Layout and browser interactions only. Cross-service erasure and receipt-cookie authority require the backend journey.
for (const viewport of [{ width: 320, height: 780 }, { width: 390, height: 844 }, { width: 844, height: 390 }, { width: 1280, height: 900 }]) {
  test(`account deletion preparation and receipt at ${viewport.width} @accountdata`, async ({ page }, info) => {
    await page.setViewportSize(viewport)
    let confirmations = 0
    await page.route(`**${endpoint}**`, async route => {
      const request = route.request()
      if (request.url().endsWith('/confirm')) {
        expect(request.method()).toBe('POST')
        expect(request.postDataJSON()).toEqual({ confirmation: 'DELETE MY ACCOUNT' })
        expect(request.headers()['x-chanter-csrf']).toBe('1')
        confirmations++
        return route.fulfill({ status: 202, json: { ...prepared, state: 'ERASING' } })
      }
      if (request.url().endsWith('/receipt')) {
        expect(request.headers().authorization).toBeUndefined()
        return route.fulfill({ json: { ...prepared, state: 'WAITING_FOR_REPLICA', replicationPending: true, parts: prepared.parts.map(part => ({ ...part, state: 'COMPLETE' })) } })
      }
      return route.fulfill({ json: prepared })
    })
    await page.goto(`/app/account-data/delete?job=${id}`)
    await expect(page.getByRole('heading', { name: 'Ready to confirm' })).toBeVisible()
    await page.screenshot({ path: info.outputPath(`fixture-ui-account-deletion-overview-${viewport.width}.png`), fullPage: true })
    const confirm = page.getByRole('button', { name: 'Permanently delete my account' })
    await expect(confirm).toBeDisabled()
    await page.getByLabel('Type DELETE MY ACCOUNT to confirm').fill('DELETE MY ACCOUNT')
    await page.getByLabel('Type DELETE MY ACCOUNT to confirm').press('Tab')
    await expect(confirm).toBeFocused()
    await expect(confirm).toBeInViewport()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: info.outputPath(`fixture-ui-account-deletion-prepared-${viewport.width}.png`), fullPage: true })
    if (viewport.width === 1280) expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([])
    await confirm.press('Enter')
    await expect(page).toHaveURL(new RegExp(`/account-deletion/${id}$`))
    await expect(page.getByRole('heading', { name: 'Recovery acknowledgement pending' })).toBeVisible()
    await expect(page.getByText('Deletion completed', { exact: true })).toHaveCount(0)
    await page.getByText('Service results', { exact: true }).click()
    expect(confirmations).toBe(1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: info.outputPath(`fixture-ui-account-deletion-receipt-${viewport.width}.png`), fullPage: true })
    let refreshRequests = 0
    page.on('request', request => { if (new URL(request.url()).pathname === '/api/v1/auth/refresh') refreshRequests++ })
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Recovery acknowledgement pending' })).toBeVisible()
    expect(refreshRequests).toBe(0)
    if (viewport.width === 390) expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([])
  })
}

test('account deletion expired preparation and unavailable receipt on phone @accountdata', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route(`**${endpoint}`, route => route.fulfill({ json: { ...prepared, preparationExpiresAt: '2020-01-01T00:00:00Z' } }))
  await page.route(`**${endpoint}/receipt`, route => route.fulfill({ status: 404, json: { message: 'Receipt unavailable' } }))
  await page.goto(`/app/account-data/delete?job=${id}`)
  await expect(page.getByText('Preparation has expired. Cancel it before preparing another request.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Permanently delete my account' })).toBeDisabled()
  await page.screenshot({ path: info.outputPath('fixture-ui-account-deletion-expired.png'), fullPage: true })
  await page.getByRole('link', { name: 'View receipt' }).click()
  await expect(page.getByRole('alert')).toContainText('This does not establish whether deletion completed.')
  await expect(page.getByText('Deletion completed', { exact: true })).toHaveCount(0)
  await page.screenshot({ path: info.outputPath('fixture-ui-account-deletion-unavailable.png'), fullPage: true })
})
