import { expect, test, type Page } from '@playwright/test'

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
    await openAndSignIn(page, ownerEmail)
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
    const homeSummaryResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/v1/me/home-summary',
    )
    await submitCredentials(page, learnerEmail)
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

  test('owner sign-out isolates route and requests before learner sign-in', async ({ page }) => {
    await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\/home/, { timeout: 30_000 })

    const ownerAccessToken = await page.evaluate(() => {
      const persisted = localStorage.getItem('chanter-auth')
      if (!persisted) return null
      const parsed = JSON.parse(persisted) as { state?: { accessToken?: string } }
      return parsed.state?.accessToken ?? null
    })
    expect(ownerAccessToken).not.toBeNull()

    const createResponse = await page.request.post('/api/v1/study-servers', {
      headers: { Authorization: `Bearer ${ownerAccessToken}` },
      data: { name: `Session isolation ${Date.now()}` },
    })
    expect(createResponse.status()).toBe(201)
    const ownerOnlyServer = await createResponse.json() as { id: string }
    const apiPathsAfterSignOut: string[] = []
    let trackAccountBoundary = false
    page.on('request', (request) => {
      if (!trackAccountBoundary) return
      const path = new URL(request.url()).pathname
      if (path.startsWith('/api/')) apiPathsAfterSignOut.push(path)
    })

    try {
      await page.goto(`/app/servers/${ownerOnlyServer.id}/community/members`)
      await expect(page).toHaveURL(new RegExp(`/app/servers/${ownerOnlyServer.id}/community/members`))

      trackAccountBoundary = true
      await page.getByRole('button', { name: 'Open account menu' }).click()
      await page.getByRole('menuitem', { name: 'Sign out' }).click()
      await expect(page).toHaveURL(/\/sign-in$/, { timeout: 15_000 })
      expect(await page.evaluate(() => window.history.state?.usr ?? null)).toBeNull()

      const learnerHomeResponse = page.waitForResponse((response) =>
        new URL(response.url()).pathname === '/api/v1/me/home-summary',
      )
      await submitCredentials(page, learnerEmail)
      await expect(page).toHaveURL(/\/app\/home$/, { timeout: 30_000 })
      expect((await learnerHomeResponse).status()).toBe(200)
      await expect(page.getByText('Loading courses…')).toHaveCount(0, { timeout: 15_000 })
      await expect(page.getByText('Loading your home…')).toHaveCount(0, { timeout: 15_000 })
      expect(apiPathsAfterSignOut.some((path) => path.includes(ownerOnlyServer.id))).toBe(false)
    } finally {
      trackAccountBoundary = false
      const cleanupResponse = await page.request.delete(`/api/v1/study-servers/${ownerOnlyServer.id}`, {
        headers: { Authorization: `Bearer ${ownerAccessToken}` },
      })
      expect(cleanupResponse.ok()).toBe(true)
    }
  })

  test('inbox and calendar routes load when signed in', async ({ page }) => {
    await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/inbox')
    await expect(page.locator('main, [class*="inbox"], h1, h2').first()).toBeVisible()
    await page.goto('/app/calendar')
    await expect(page.locator('main, [class*="calendar"], h1, h2').first()).toBeVisible()
  })

  test('teaching and billing settings routes load for owner', async ({ page }) => {
    await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/teaching')
    await expect(page.locator('main, h1, h2').first()).toBeVisible()
    await page.goto('/app/settings/billing')
    await expect(page.locator('main, h1, h2').first()).toBeVisible()
  })

  test('friends page loads', async ({ page }) => {
    await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/friends')
    await expect(page.locator('main, h1, h2').first()).toBeVisible()
  })
})

async function openAndSignIn(page: Page, email: string) {
  await page.goto('/sign-in')
  await submitCredentials(page, email)
}

async function submitCredentials(page: Page, email: string) {
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(demoPassword)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
}
