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

  test('learner can sign in and open a course workspace area', async ({ page }) => {
    await page.goto('/sign-in')
    await page.getByLabel('Email').fill(learnerEmail)
    await page.getByLabel('Password').fill(demoPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/home')
    await expect(page.getByRole('heading', { name: /home|welcome|courses/i }).first()).toBeVisible({ timeout: 15_000 })
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
