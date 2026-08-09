import { expect, test } from '@playwright/test'

const demoPassword = process.env.DEMO_PASSWORD ?? 'chanter-dev-demo'
const ownerEmail = process.env.DEMO_OWNER_EMAIL ?? 'dev-demo-owner@chanter.local'
const learnerEmail = process.env.DEMO_LEARNER_EMAIL ?? 'dev-demo-learner@chanter.local'
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173'

/**
 * Full product critical paths (@product). Requires `make product-up` + `make product-demo-seed`
 * and PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1:5173.
 */
test.describe('Product critical paths @product', () => {
  test.skip(!process.env.PLAYWRIGHT_PRODUCT, 'Set PLAYWRIGHT_PRODUCT=1 after product-up + demo-seed')

  test.use({ baseURL })

  test('owner can sign in and reach home', async ({ page }) => {
    await page.goto('/sign-in')
    await page.getByLabel('Email').fill(ownerEmail)
    await page.getByLabel('Password').fill(demoPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL(/\/app\/home/, { timeout: 30_000 })
  })

  test('learner home and sidebar load without unexpected API errors', async ({ page }) => {
    const unexpectedApiResponses: string[] = []
    page.on('response', (response) => {
      const url = new URL(response.url())
      if (url.pathname.startsWith('/api/') && response.status() >= 400) {
        unexpectedApiResponses.push(`${response.status()} ${response.request().method()} ${url.pathname}`)
      }
    })

    await page.goto('/sign-in')
    await page.locator('input[autocomplete="email"]').fill(learnerEmail)
    await page.locator('input[autocomplete="current-password"]').fill(demoPassword)
    const homeSummaryResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/v1/me/home-summary',
    )
    await page.locator('form.v2-auth-form button[type="submit"]').click()
    await expect(page).toHaveURL(/\/app\/home/, { timeout: 30_000 })
    expect((await homeSummaryResponse).status()).toBe(200)
    await expect(page.getByText('Loading courses…')).toHaveCount(0, { timeout: 15_000 })
    await expect(page.getByText('Loading your home…')).toHaveCount(0, { timeout: 15_000 })
    await expect(page.getByRole('heading', {
      level: 1,
      name: /^Good (morning|afternoon|evening),/,
    })).toBeVisible({ timeout: 15_000 })
    expect(unexpectedApiResponses).toEqual([])
  })

  test('inbox and calendar routes load when signed in', async ({ page }) => {
    await page.goto('/sign-in')
    await page.getByLabel('Email').fill(ownerEmail)
    await page.getByLabel('Password').fill(demoPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/inbox')
    await expect(page.locator('main, [class*="inbox"], h1, h2').first()).toBeVisible()
    await page.goto('/app/calendar')
    await expect(page.locator('main, [class*="calendar"], h1, h2').first()).toBeVisible()
  })

  test('teaching and billing settings routes load for owner', async ({ page }) => {
    await page.goto('/sign-in')
    await page.getByLabel('Email').fill(ownerEmail)
    await page.getByLabel('Password').fill(demoPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/teaching')
    await expect(page.locator('main, h1, h2').first()).toBeVisible()
    await page.goto('/app/settings/billing')
    await expect(page.locator('main, h1, h2').first()).toBeVisible()
  })

  test('friends page loads', async ({ page }) => {
    await page.goto('/sign-in')
    await page.getByLabel('Email').fill(ownerEmail)
    await page.getByLabel('Password').fill(demoPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/friends')
    await expect(page.locator('main, h1, h2').first()).toBeVisible()
  })
})
