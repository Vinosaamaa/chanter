import { randomUUID } from 'node:crypto'
import { type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'
import { expect, test } from './release-test'

const appUrl = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173'
const inboxUrl = 'http://127.0.0.1:8025'

// These tests handle one-time credentials. Never publish artifacts containing them.
test.use({ trace: 'off', video: 'off', screenshot: 'off' })

async function deliveredLink(request: APIRequestContext, email: string, subject: string, path: string) {
  let link: URL | undefined
  await expect.poll(async () => {
    const response = await request.get(`${inboxUrl}/api/v1/messages?limit=100`)
    expect(response.ok()).toBe(true)
    const inbox = await response.json() as { messages: { ID: string, Subject: string, To: { Address: string }[] }[] }
    const match = inbox.messages.find((message) => message.Subject === subject
      && message.To.some((recipient) => recipient.Address === email))
    if (!match) return false
    const messageResponse = await request.get(`${inboxUrl}/api/v1/message/${encodeURIComponent(match.ID)}`)
    expect(messageResponse.ok()).toBe(true)
    const message = await messageResponse.json() as { Text: string }
    const raw = message.Text.match(/https?:\/\/[^\s<>]+\?token=[a-f0-9]+/i)?.[0]
    if (!raw) return false
    link = new URL(raw)
    return true
  }, { timeout: 30_000, message: 'Expected transactional email was not delivered to the test inbox' }).toBe(true)
  expect(link!.pathname).toBe(path)
  // Keep the actual environment-correct link. Do not silently rewrite a bad deployment origin.
  expect(link!.origin).toBe(new URL(process.env.CHANTER_PUBLIC_BASE_URL ?? 'http://localhost:5173').origin)
  return link!.toString()
}

async function signIn(page: Page, email: string, password: string) {
  await page.goto(new URL('/sign-in', appUrl).toString())
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(/\/app\/home/)
}

async function signOut(page: Page) {
  await page.getByRole('button', { name: 'Open account menu' }).click()
  const completed = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/auth/logout'
    && response.request().method() === 'POST')
  await page.getByRole('menuitem', { name: 'Sign out', exact: true }).click()
  const response = await completed
  expect(response.status()).toBe(204)
  // Chromium may never emit a completion event for a bodyless 204. Verify the browser effect instead.
  await expect.poll(async () => (await page.context().cookies()).some((cookie) => cookie.name === 'chanter_refresh')).toBe(false)
  await expect(page).toHaveURL(/\/sign-in/)
}

async function assertCredentialBoundary(page: Page, context: BrowserContext) {
  const cookie = (await context.cookies()).find((item) => item.name === 'chanter_refresh')
  expect(cookie).toBeDefined()
  expect(cookie!.httpOnly).toBe(true)
  expect(cookie!.secure).toBe(true)
  expect(cookie!.sameSite).toBe('Strict')
  expect(cookie!.path).toBe('/api/v1/auth')
  const storage = await page.evaluate(() => ({
    local: Object.values(localStorage).join(' '),
    session: Object.values(sessionStorage).join(' '),
    cookie: document.cookie,
  }))
  expect(storage.cookie).not.toContain('chanter_refresh')
  expect(/accessToken|refreshToken|eyJ[A-Za-z0-9_-]+\./.test(storage.local)).toBe(false)
  expect(/accessToken|refreshToken|eyJ[A-Za-z0-9_-]+\./.test(storage.session)).toBe(false)
  return cookie!
}

test.describe('Verified account and recovery @product', () => {
  test.skip(!process.env.PLAYWRIGHT_PRODUCT, 'Requires product services and the local SMTP inbox')

  test('concurrent registrations remain neutral and deliver a usable verification link', async ({ page, request }) => {
    const email = `concurrent-e2e-${randomUUID()}@example.com`
    const password = `Chanter-${randomUUID()}`
    const responses = await Promise.all(Array.from({ length: 6 }, () => request.post(
      new URL('/api/v1/auth/register', appUrl).toString(), {
        headers: { Origin: new URL(appUrl).origin, 'X-Chanter-CSRF': '1' },
        data: { email, password, displayName: 'Concurrent learner' },
      },
    )))
    for (const response of responses) {
      expect(response.status()).toBe(202)
      expect(await response.json()).not.toHaveProperty('accessToken')
    }
    await page.goto(await deliveredLink(request, email, 'Verify your Chanter email', '/verify-email'))
    await expect(page.getByRole('status')).toContainText(/verified/i)
    await signIn(page, email, password)
  })

  test('register, verify, restore, rotate and sign out through real services', async ({ page, context, request }) => {
    const email = `auth-e2e-${randomUUID()}@example.com`
    const password = `Chanter-${randomUUID()}`
    await page.goto(new URL('/sign-in', appUrl).toString())
    await page.getByRole('tab', { name: 'Create account', exact: true }).click()
    await page.getByLabel('Full name', { exact: true }).fill('Auth journey learner')
    await page.getByLabel('Email', { exact: true }).fill(email)
    await page.getByLabel('Password', { exact: true }).fill(password)
    const registration = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/auth/register')
    await page.getByRole('button', { name: 'Create account', exact: true }).click()
    expect((await registration).status()).toBe(202)
    await expect(page.getByRole('status')).toContainText('check your inbox')
    const verifyLink = await deliveredLink(request, email, 'Verify your Chanter email', '/verify-email')
    await page.goto(verifyLink)
    await expect(page.getByRole('status')).toContainText(/verified/i)
    await signIn(page, email, password)
    const original = await assertCredentialBoundary(page, context)
    await page.reload()
    await expect(page).toHaveURL(/\/app\/home/)
    await expect(page.getByRole('button', { name: 'Open account menu' })).toBeVisible()
    const rotated = await assertCredentialBoundary(page, context)
    expect(rotated.value === original.value).toBe(false)

    // A separate API context owns a second device's real refresh cookie. Its
    // revocation must be visible in the UI and enforced by the auth service.
    const headers = { Origin: new URL(appUrl).origin, 'X-Chanter-CSRF': '1' }
    const secondDevice = await request.post(new URL('/api/v1/auth/login', appUrl).toString(), {
      headers: { ...headers, 'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0' },
      data: { email, password },
    })
    expect(secondDevice.ok()).toBe(true)
    const deviceCookie = secondDevice.headersArray()
      .find((header) => header.name.toLowerCase() === 'set-cookie' && header.value.startsWith('chanter_refresh='))
      ?.value.split(';')[0]
    expect(Boolean(deviceCookie)).toBe(true)
    await page.getByRole('button', { name: 'Open account menu' }).click()
    await page.getByRole('menuitem', { name: 'Sessions and devices', exact: true }).click()
    const devices = page.getByRole('dialog', { name: 'Sessions and devices' })
    await expect(devices.getByText('This device', { exact: true })).toBeVisible()
    await devices.getByRole('button', { name: 'Sign out Firefox on Linux', exact: true }).click()
    await expect(devices.getByRole('button', { name: 'Sign out Firefox on Linux', exact: true })).toHaveCount(0)
    // API cookie jars do not consistently send Secure cookies over loopback HTTP.
    // The browser's real cookie behavior is asserted separately above.
    const revoked = await request.post(new URL('/api/v1/auth/refresh', appUrl).toString(), {
      headers: { ...headers, Cookie: deviceCookie! },
    })
    expect(revoked.status()).toBe(401)
    await page.keyboard.press('Escape')
    await expect(devices).toHaveCount(0)
    await signOut(page)
    await page.reload()
    await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()
    expect((await context.cookies()).some((cookie) => cookie.name === 'chanter_refresh')).toBe(false)
  })

  test('password recovery delivers a usable link and revokes the old session', async ({ page, context, request }) => {
    const email = `reset-e2e-${randomUUID()}@example.com`
    const password = `Chanter-${randomUUID()}`
    const newPassword = `Reset-${randomUUID()}`
    const origin = new URL(appUrl).origin
    const headers = { Origin: origin, 'X-Chanter-CSRF': '1' }
    const registration = await request.post(new URL('/api/v1/auth/register', appUrl).toString(), {
      headers, data: { email, password, displayName: 'Recovery learner' },
    })
    expect(registration.status()).toBe(202)
    await page.goto(await deliveredLink(request, email, 'Verify your Chanter email', '/verify-email'))
    await expect(page.getByRole('status')).toContainText(/verified/i)
    await signIn(page, email, password)
    const beforeReset = await assertCredentialBoundary(page, context)
    await page.goto(new URL('/forgot-password', appUrl).toString())
    await page.getByLabel('Email', { exact: true }).fill(email)
    await page.getByRole('button', { name: 'Send reset link' }).click()
    await expect(page.getByRole('status')).toBeVisible()
    await page.goto(await deliveredLink(request, email, 'Reset your Chanter password', '/reset-password'))
    await page.getByLabel('New password', { exact: true }).fill(newPassword)
    await page.getByRole('button', { name: 'Update password' }).click()
    await expect(page.getByRole('status')).toContainText(/updated|reset/i)
    // APIRequestContext checks the rejected session without manufacturing a browser console failure.
    const rejected = await request.post(new URL('/api/v1/auth/refresh', appUrl).toString(), {
      headers: { ...headers, Cookie: `chanter_refresh=${beforeReset.value}` },
    })
    expect(rejected.status()).toBe(401)
    await context.clearCookies()
    await signIn(page, email, newPassword)
    await assertCredentialBoundary(page, context)
  })
})
