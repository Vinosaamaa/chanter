import { expect, test } from '@playwright/test'
import { VISUAL_NOW } from './workspace-fixtures'

// Synthetic layout/state proof. Actual audio and backend behavior run separately.
test.beforeEach(async ({ page }) => {
  await page.routeWebSocket('**/api/v1/realtime/ws', socket => {
    socket.onMessage(message => {
      const frame = JSON.parse(String(message))
      if (frame.type === 'subscribe') socket.send(JSON.stringify({ type: 'subscribed', channelId: frame.channelId, channelScope: frame.channelScope }))
    })
  })
})

test('fixture UI Teaching retries unavailable server authority @teaching', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 })
  let fail = true
  let dashboardRequests = 0
  page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/instructor-dashboard')) dashboardRequests++ })
  await page.route('**/study-servers', route => fail
    ? route.fulfill({ status: 502, contentType: 'application/json', body: '{}' })
    : route.continue())
  await page.goto('/app/teaching?serverId=removed-server&visual=staff')
  await expect(page.getByRole('alert')).toHaveText('Usage and teaching information are temporarily unavailable. Please try again.', { timeout: 15000 })
  expect(dashboardRequests).toBe(0)
  fail = false
  await page.getByRole('button', { name: 'Refresh teaching' }).click()
  await expect(page.getByText('58 runs remaining of the lifetime limit')).toBeVisible()
  await expect(page).toHaveURL(/serverId=visual-study/)
  expect(dashboardRequests).toBeGreaterThan(0)
})

for (const width of [390, 844, 1280]) {
  test(`fixture UI Teaching bookmark and refresh at ${width} @teaching`, async ({ page }, info) => {
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.clock.setFixedTime(new Date(VISUAL_NOW))
    await page.setViewportSize({ width, height: width === 844 ? 390 : 900 })
    let fail = false
    let release!: () => void
    let arrived!: () => void
    const pending = new Promise<void>(resolve => { release = resolve })
    const requested = new Promise<void>(resolve => { arrived = resolve })
    await page.route('**/instructor-dashboard', async route => {
      if (!fail) return route.continue()
      arrived()
      await pending
      await route.fulfill({ status: 502, contentType: 'application/json', body: '{}' })
    })
    await page.goto('/app/instructor-dashboard?serverId=visual-study&visual=staff')
    await expect(page).toHaveURL(/\/app\/teaching\?serverId=visual-study&visual=staff/)
    await expect(page.getByText('58 runs remaining of the lifetime limit')).toBeVisible()
    await expect(page.getByText('Low-confidence handoffs', { exact: true })).toBeVisible()
    await expect(page.getByText('Office Hours waitlist', { exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath(`fixture-ui-teaching-bookmark-${width}.png`) })
    await page.locator('.teaching-operational-summary').scrollIntoViewIfNeeded()
    await expect(page.getByText('Office Hours waitlist', { exact: true })).toBeInViewport()
    await page.screenshot({ path: info.outputPath(`fixture-ui-teaching-summary-${width}.png`) })
    fail = true
    await page.getByRole('button', { name: 'Refresh teaching' }).click()
    await requested
    await expect(page.getByText('Loading dashboard...', { exact: true })).toBeVisible()
    await expect(page.getByText('58 runs remaining of the lifetime limit')).toHaveCount(0)
    release()
    await expect(page.getByRole('alert')).toHaveText('Usage and teaching information are temporarily unavailable. Please try again.')
    await page.screenshot({ path: info.outputPath(`fixture-ui-teaching-unavailable-${width}.png`) })
    fail = false
    await page.getByRole('button', { name: 'Refresh teaching' }).click()
    await expect(page.getByText('58 runs remaining of the lifetime limit')).toBeVisible()
    expect(errors).toEqual([])
  })
}
