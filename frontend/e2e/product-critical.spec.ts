import { type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'

import { expect, expectNoHorizontalOverflow, test } from './release-test'

const demoPassword = process.env.DEMO_PASSWORD ?? 'chanter-dev-demo'
const ownerEmail = process.env.DEMO_OWNER_EMAIL ?? 'dev-demo-owner@chanter.local'
const memberEmail = process.env.DEMO_MEMBER_EMAIL ?? 'dev-demo-member@chanter.local'
const learnerEmail = process.env.DEMO_LEARNER_EMAIL ?? 'dev-demo-learner@chanter.local'
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173'

// Authenticated traces contain bearer tokens and HttpOnly cookie values.
test.use({ baseURL, trace: 'off', video: 'off', screenshot: 'off' })

/**
 * Full product critical paths (@product). Requires `make product-up` + `make product-demo-seed`
 * and PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1:5173.
 */
test.describe('Product critical paths @product', () => {
  test.skip(!process.env.PLAYWRIGHT_PRODUCT, 'Set PLAYWRIGHT_PRODUCT=1 after product-up + demo-seed')

  test('owner can sign in and reach home', async ({ page }) => {
    await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\/home/, { timeout: 30_000 })
    await expect(page.getByText('Loading courses…')).toHaveCount(0, { timeout: 15_000 })
    await expect(page.getByText('Loading your home…')).toHaveCount(0, { timeout: 15_000 })
    await expect(page.getByRole('heading', {
      level: 1,
      name: /^Good (morning|afternoon|evening),/,
    })).toBeVisible()
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

  test('generic member can load Study Server navigation without Course access', async ({ page }) => {
    const { accessToken } = await openAndSignIn(page, memberEmail)
    await expect(page).toHaveURL(/\/app\/home/, { timeout: 30_000 })
    await expect(page.getByText('Loading courses…')).toHaveCount(0, { timeout: 15_000 })
    await expect(page.getByText('Loading your home…')).toHaveCount(0, { timeout: 15_000 })
    await expect(page.getByRole('heading', {
      level: 1,
      name: /^Good (morning|afternoon|evening),/,
    })).toBeVisible({ timeout: 15_000 })

    expect(accessToken).not.toBeNull()

    const serversResponse = await page.request.get('/api/v1/study-servers', {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect(serversResponse.status()).toBe(200)
    const servers = await serversResponse.json() as Array<{ id: string, name: string }>
    const demoServer = servers.find((server) => server.name === 'Workable Product Demo')
    expect(demoServer).toBeDefined()

    const navigationResponse = await page.request.get(
      `/api/v1/study-servers/${demoServer?.id}/navigation`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    )
    expect(navigationResponse.status()).toBe(200)
    const navigation = await navigationResponse.json() as {
      courses: unknown[]
      studyServerChannels: Array<{ name: string }>
    }
    expect(navigation.courses).toEqual([])
    expect(navigation.studyServerChannels.some((channel) => channel.name === 'general')).toBe(true)
  })

  test('owner sign-out isolates route and requests before learner sign-in', async ({ page }) => {
    const { accessToken: ownerAccessToken } = await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\/home/, { timeout: 30_000 })

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
      // The URL changes before cookie restoration and the initial member fetch.
      // Start the sign-out boundary only after this owner's page has actually loaded.
      await expect(page.getByText(/\(You\)$/)).toBeVisible()
      await expect(page.getByText('Loading courses…')).toHaveCount(0)

      await page.getByRole('button', { name: 'Open account menu' }).click()
      trackAccountBoundary = true
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
      const { accessToken: cleanupAccessToken } = await openAndSignIn(page, ownerEmail)
      const cleanupResponse = await page.request.delete(`/api/v1/study-servers/${ownerOnlyServer.id}`, {
        headers: { Authorization: `Bearer ${cleanupAccessToken}` },
      })
      expect(cleanupResponse.ok()).toBe(true)
    }
  })

  test('inbox and calendar routes load when signed in', async ({ page }) => {
    await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/inbox')
    await expect(page.getByRole('heading', { level: 1, name: 'Inbox' })).toBeVisible()
    await expect(page.getByText('Loading…')).toHaveCount(0, { timeout: 15_000 })
    await page.goto('/app/calendar')
    await expect(page.getByRole('region', { name: 'Calendar' })).toBeVisible()
    await expect(page.getByText('Loading calendar…')).toHaveCount(0, { timeout: 15_000 })
  })

  test('owner sees actual free-beta usage and cannot patch a higher quota', async ({ page }) => {
    const { accessToken } = await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/teaching')
    await expect(page.getByRole('heading', { level: 1, name: 'Teaching', exact: true })).toBeVisible()
    await expect(page.getByText('Loading dashboard...')).toHaveCount(0, { timeout: 15_000 })
    await page.goto('/app/settings/billing')
    await expect(page).toHaveURL(/\/app\/settings\/usage$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Usage' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Free beta' })).toBeVisible()
    await expect(page.getByRole('button', { name: /save plan|upgrade|checkout/i })).toHaveCount(0)
    await expect(page.getByText('Loading usage…')).toHaveCount(0, { timeout: 15_000 })
    const headers = { Authorization: `Bearer ${accessToken}` }
    const servers = await (await page.request.get('/api/v1/study-servers', { headers })).json() as Array<{ id: string; name: string }>
    const server = servers.find(server => server.name === 'Workable Product Demo')!
    expect(server).toBeDefined()
    const selector = page.getByRole('combobox', { name: 'Select Study Server' })
    if (await selector.count()) await selector.selectOption(server.id)
    const dashboardResponse = await page.request.get(`/api/v1/study-servers/${server.id}/instructor-dashboard`, { headers })
    expect(dashboardResponse.status()).toBe(200)
    const dashboard = await dashboardResponse.json() as { aiInvocationCount: number; aiInvocationLimit: number; remainingAiInvocations: number }
    const usage = `${dashboard.aiInvocationCount.toLocaleString()} of ${dashboard.aiInvocationLimit.toLocaleString()} assistant runs used, ${dashboard.remainingAiInvocations.toLocaleString()} remaining`
    await expect(page.getByText(usage, { exact: true })).toBeVisible()
    const planPath = `/api/v1/study-servers/${server.id}/saas-plan`
    const before = await (await page.request.get(planPath, { headers })).json()
    expect(before).toMatchObject({ planTier: 'FREE_BETA', entitlementSource: 'OPERATOR_POLICY', usageWindow: 'LIFETIME' })
    expect((await page.request.patch(planPath, { headers, data: { planTier: 'ORGANIZATION' } })).status()).toBe(403)
    expect(await (await page.request.get(planPath, { headers })).json()).toEqual(before)
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Free beta' })).toBeVisible()
    if (await selector.count()) await selector.selectOption(server.id)
    for (const width of [1280, 390]) {
      await page.setViewportSize({ width, height: 900 })
      await expect(page.getByText(usage, { exact: true })).toBeVisible()
      await expect(page.getByText(/does not reset monthly/)).toBeVisible()
      await expectNoHorizontalOverflow(page)
    }
  })

  test('friends page loads', async ({ page }) => {
    await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/friends')
    await expect(page.getByRole('heading', { level: 1, name: 'Friends' })).toBeVisible()
    await expect(page.getByText('Loading friends…')).toHaveCount(0, { timeout: 15_000 })
  })

  test('restarted consumers produce persistent searchable announcements and Inbox items', async ({ page }, testInfo) => {
    const proof = JSON.parse(readFileSync('../.product/durable-events-proof.json', 'utf8')) as { serverId: string; title: string }
    await openAndSignIn(page, memberEmail)
    await expect(page).toHaveURL(/\/app\//, { timeout: 30_000 })
    await page.goto('/app/inbox')
    await expect(page.locator('.inbox-thread-list').getByText(proof.title, { exact: true })).toBeVisible({ timeout: 15_000 })
    await page.reload()
    await expect(page.locator('.inbox-thread-list').getByText(proof.title, { exact: true })).toBeVisible({ timeout: 15_000 })
    await page.goto(`/app/servers/${proof.serverId}/community/announcements`)
    await expect(page.getByRole('heading', { name: proof.title, exact: true })).toBeVisible({ timeout: 15_000 })
    for (const width of [1280, 390]) {
      await page.setViewportSize({ width, height: 900 })
      await page.keyboard.press('Control+k')
      const dialog = page.getByRole('dialog', { name: 'Global search' })
      await expect(dialog).toBeVisible()
      await dialog.getByRole('textbox').fill(proof.title)
      await expect(dialog.getByText(proof.title, { exact: true })).toBeVisible({ timeout: 15_000 })
      await expect(dialog.getByRole('button', { name: 'Refresh index' })).toHaveCount(0)
      await expectNoHorizontalOverflow(page)
      await testInfo.attach(`durable-search-${width}`, { body: await page.screenshot(), contentType: 'image/png' })
      await dialog.getByText(proof.title, { exact: true }).click()
      await expect(dialog).toHaveCount(0)
      await expect(page).toHaveURL(new RegExp(`/app/servers/${proof.serverId}/community/announcements`))
    }
  })

  test('signed-in home remains content-ready without horizontal overflow @viewport', async ({ page }) => {
    await openAndSignIn(page, ownerEmail)
    await expect(page).toHaveURL(/\/app\/home/, { timeout: 30_000 })
    await expect(page.getByText('Loading courses…')).toHaveCount(0, { timeout: 15_000 })
    await expect(page.getByText('Loading your home…')).toHaveCount(0, { timeout: 15_000 })
    await expect(page.getByRole('heading', {
      level: 1,
      name: /^Good (morning|afternoon|evening),/,
    })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})

async function openAndSignIn(page: Page, email: string) {
  // Clear the shared-cookie session before choosing an account for this journey.
  if (new URL(page.url()).protocol.startsWith('http')) {
    await page.request.post('/api/v1/auth/logout', { headers: { Origin: baseURL, 'X-Chanter-CSRF': '1' } })
  }
  await page.goto('/sign-in')
  return submitCredentials(page, email)
}

async function submitCredentials(page: Page, email: string) {
  const loginResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === '/api/v1/auth/login' && response.request().method() === 'POST',
  )
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(demoPassword)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  const response = await loginResponse
  expect(response.status()).toBe(200)
  return response.json() as Promise<{ accessToken: string }>
}
