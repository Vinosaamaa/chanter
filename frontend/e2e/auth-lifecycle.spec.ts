import { spawnSync } from 'node:child_process'
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
  // The preceding verification page can still be loading its self-hosted font.
  // Settle it before this intentional full navigation; real font failures still fail.
  await page.evaluate(() => document.fonts.ready)
  await page.goto(new URL('/sign-in', appUrl).toString())
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  const homeResponses = Promise.all([
    '/api/v1/study-servers', '/api/v1/me/home-summary',
    '/api/v1/study-server-invitations', '/api/v1/me/notifications/unread-count',
  ].map(path => page.waitForResponse(response => new URL(response.url()).pathname === path && response.request().method() === 'GET')))
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(/\/app\/home/)
  for (const response of await homeResponses) {
    expect(response.status()).toBe(200)
    expect(await response.finished()).toBeNull()
  }
  await expect(page.getByRole('heading', { level: 1, name: /^Good (morning|afternoon|evening),/ })).toBeVisible()
  await expect(page.getByText('Loading your courses…')).toHaveCount(0)
  await expect(page.getByText('Loading courses…', { exact: true })).toHaveCount(0)
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

async function sourceTestActor(request: APIRequestContext, role: string) {
  const email = `source-${role}-${randomUUID()}@example.com`
  const password = `Chanter-${randomUUID()}`
  const headers = { Origin: new URL(appUrl).origin, 'X-Chanter-CSRF': '1' }
  const registered = await request.post(new URL('/api/v1/auth/register', appUrl).toString(), {
    headers, data: { email, password, displayName: `Source cleanup ${role}` },
  })
  expect(registered.status()).toBe(202)
  const verification = new URL(await deliveredLink(request, email, 'Verify your Chanter email', '/verify-email'))
  const verified = await request.post(new URL('/api/v1/auth/verify-email', appUrl).toString(), {
    headers, data: { token: verification.searchParams.get('token') },
  })
  expect(verified.status()).toBe(200)
  const login = await request.post(new URL('/api/v1/auth/login', appUrl).toString(), { headers, data: { email, password } })
  expect(login.status()).toBe(200)
  const accessToken: unknown = (await login.json()).accessToken
  expect(typeof accessToken === 'string' && accessToken.length > 0).toBe(true)
  return { email, password, headers: { Authorization: `Bearer ${accessToken}` } }
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

  test('export, cancel preparation, confirm deletion and reload the signed-out receipt', async ({ page, context, request }) => {
    test.skip(!process.env.PLAYWRIGHT_LIFECYCLE_PREVIEW, 'Requires the pinned account-backend dependency preview until final union acceptance')
    test.setTimeout(180_000)
    const email = `lifecycle-e2e-${randomUUID()}@example.com`
    const password = `Chanter-${randomUUID()}`
    const headers = { Origin: new URL(appUrl).origin, 'X-Chanter-CSRF': '1' }
    const registration = await request.post(new URL('/api/v1/auth/register', appUrl).toString(), {
      headers, data: { email, password, displayName: 'Account data learner' },
    })
    expect(registration.status()).toBe(202)
    await page.goto(await deliveredLink(request, email, 'Verify your Chanter email', '/verify-email'))
    await expect(page.getByRole('status')).toContainText(/verified/i)
    await signIn(page, email, password)
    const sessionCookie = await assertCredentialBoundary(page, context)
    await page.getByRole('button', { name: 'Open account menu' }).click()
    await page.getByRole('menuitem', { name: 'Account data', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Account data', exact: true, level: 1 })).toBeVisible()
    const exportCreated = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/auth/account/exports' && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Request export' }).click()
    const exportResponse = await exportCreated
    expect(exportResponse.status()).toBe(202)
    const exportJobId = (await exportResponse.json()).id as string
    expect(/^[a-f0-9-]{36}$/.test(exportJobId)).toBe(true)
    await expect(page.getByText('Export requested. Refresh status to check its progress.')).toBeVisible()
    await expect.poll(async () => {
      await page.getByRole('button', { name: 'Refresh status' }).click()
      await expect(page.getByRole('button', { name: 'Refresh status' })).toBeEnabled()
      return page.getByRole('button', { name: 'Download ZIP' }).count()
    }, { timeout: 60_000, intervals: [1000, 2000, 3000] }).toBe(1)
    const downloading = page.waitForEvent('download')
    await page.getByRole('button', { name: 'Download ZIP' }).click()
    const archive = await downloading
    const nativeUrl = new URL(archive.url())
    expect(nativeUrl.origin === new URL(appUrl).origin && nativeUrl.pathname === `/api/v1/auth/account/exports/${exportJobId}/download` && nativeUrl.search === '' && nativeUrl.hash === '').toBe(true)
    expect(archive.suggestedFilename()).toMatch(/^chanter-account-[a-f0-9-]+\.zip$/)
    expect(await archive.failure()).toBeNull()
    const stream = await archive.createReadStream()
    expect(stream).not.toBeNull()
    const chunks: Buffer[] = []
    let bytes = 0
    for await (const chunk of stream!) {
      const buffer = Buffer.from(chunk)
      bytes += buffer.length
      expect(bytes).toBeLessThan(5 * 1024 * 1024)
      chunks.push(buffer)
    }
    // Fresh synthetic account only. Validate ZIP ending, CRCs and seven-source coverage
    // without writing or printing the archive, account data or credential values.
    const checked = spawnSync('python3', ['-c', `
import io, json, sys, zipfile
try:
    with zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())) as archive:
        entries = archive.infolist()
        assert len(entries) < 200 and sum(e.file_size for e in entries) < 10 * 1024 * 1024
        assert archive.testzip() is None
        manifest = json.loads(archive.read('manifest.json'))
        assert manifest['schemaVersion'] == 1
        assert {item['source'] for item in manifest['sources']} == {'auth', 'community', 'message', 'media', 'agent', 'search', 'notification'}
except Exception:
    sys.exit(2)
`], { input: Buffer.concat(chunks), timeout: 10_000, maxBuffer: 1024 })
    expect(checked.status, 'Downloaded export must be a complete valid seven-source ZIP').toBe(0)
    await archive.delete()
    await page.getByRole('link', { name: 'Review account deletion' }).click()
    await page.getByRole('button', { name: 'Prepare deletion' }).click()
    await expect.poll(async () => {
      await page.getByRole('button', { name: 'Refresh status' }).click()
      await expect(page.getByRole('button', { name: 'Refresh status' })).toBeEnabled()
      return page.getByRole('heading', { name: 'Ready to confirm' }).count()
    }, { timeout: 30_000, intervals: [1000, 2000] }).toBe(1)
    const firstJob = new URL(page.url()).searchParams.get('job')
    await page.getByRole('button', { name: 'Cancel preparation' }).click()
    await expect.poll(async () => {
      await page.getByRole('button', { name: 'Refresh status' }).click()
      await expect(page.getByRole('button', { name: 'Refresh status' })).toBeEnabled()
      return page.getByRole('heading', { name: 'Preparation cancelled' }).count()
    }, { timeout: 30_000, intervals: [1000, 2000] }).toBe(1)
    await page.getByRole('button', { name: 'Prepare a new request' }).click()
    await expect.poll(async () => {
      await page.getByRole('button', { name: 'Refresh status' }).click()
      await expect(page.getByRole('button', { name: 'Refresh status' })).toBeEnabled()
      return page.getByRole('heading', { name: 'Ready to confirm' }).count()
    }, { timeout: 30_000, intervals: [1000, 2000] }).toBe(1)
    const job = new URL(page.url()).searchParams.get('job')
    expect(job).not.toBe(firstJob)
    const receiptPath = `/api/v1/auth/account/deletions/${job}/receipt`
    const receiptCookie = (await context.cookies()).find(cookie => cookie.path === receiptPath)
    expect(Boolean(receiptCookie)).toBe(true)
    expect({ httpOnly: receiptCookie?.httpOnly, secure: receiptCookie?.secure, sameSite: receiptCookie?.sameSite }).toEqual({ httpOnly: true, secure: true, sameSite: 'Strict' })
    const confirm = page.getByRole('button', { name: 'Permanently delete my account' })
    await expect(confirm).toBeDisabled()
    await page.getByLabel('Type DELETE MY ACCOUNT to confirm').fill('DELETE MY ACCOUNT')
    const receipt = page.waitForResponse(response => new URL(response.url()).pathname === receiptPath)
    await confirm.click()
    const receiptResponse = await receipt
    expect(receiptResponse.status()).toBe(200)
    expect(receiptResponse.request().method()).toBe('GET')
    expect(receiptResponse.request().headers().authorization === undefined).toBe(true)
    expect(['ERASING', 'WAITING_FOR_REPLICA', 'COMPLETE']).toContain((await receiptResponse.json()).state)
    await expect(page).toHaveURL(new RegExp(`/account-deletion/${job}$`))
    await expect(page.getByRole('heading', { name: 'Deletion status', exact: true })).toBeVisible()
    await expect.poll(() => page.evaluate(() => localStorage.getItem('chanter-session-change')?.startsWith('signed-out:'))).toBe(true)
    const revoked = await request.post(new URL('/api/v1/auth/refresh', appUrl).toString(), {
      headers: { ...headers, Cookie: `chanter_refresh=${sessionCookie.value}` },
    })
    expect(revoked.status()).toBe(401)
    const restoredReceipt = page.waitForResponse(response => new URL(response.url()).pathname === receiptPath)
    const refreshRequests: string[] = []
    page.on('request', req => { if (new URL(req.url()).pathname === '/api/v1/auth/refresh') refreshRequests.push(req.method()) })
    await page.reload()
    const reloaded = await restoredReceipt
    expect(reloaded.status()).toBe(200)
    expect(reloaded.request().method()).toBe('GET')
    expect(reloaded.request().headers().authorization === undefined).toBe(true)
    await expect(page.getByRole('heading', { name: 'Deletion status', exact: true })).toBeVisible()
    expect(refreshRequests).toEqual([])
  })

  test('real source deletion closes access and preserves requester progress after reload', async ({ page, request, playwright }) => {
    test.skip(!process.env.PLAYWRIGHT_LIFECYCLE_PREVIEW, 'Requires the pinned source-lifecycle dependency preview until final union acceptance')
    test.setTimeout(180_000)
    const owner = await sourceTestActor(request, 'owner')
    const member = await sourceTestActor(request, 'member')
    const anonymous = await playwright.request.newContext({ baseURL: appUrl })
    try {
      const created = await request.post(new URL('/api/v1/study-servers', appUrl).toString(), {
        headers: owner.headers, data: { name: `Source cleanup ${randomUUID()}` },
      })
      expect(created.status()).toBe(201)
      const server = await created.json() as { id: string }
      const createdCourse = await request.post(new URL(`/api/v1/study-servers/${server.id}/courses`, appUrl).toString(), {
        headers: owner.headers, data: { title: 'Field observation', cohortName: 'Weekend field group' },
      })
      expect(createdCourse.status()).toBe(201)
      const course = await createdCourse.json() as { id: string; cohort: { id: string } }
      const enrolled = await request.post(new URL(`/api/v1/cohorts/${course.cohort.id}/enrollments`, appUrl).toString(), {
        headers: owner.headers, data: { email: member.email },
      })
      expect(enrolled.status()).toBe(201)
      const bytes = Buffer.from('Synthetic field notes for source-deletion acceptance.\n')
      const uploaded = await request.post(new URL(`/api/v1/courses/${course.id}/course-resources`, appUrl).toString(), {
        headers: owner.headers, multipart: { title: 'Field notes', aiApproved: 'false', file: { name: 'field-notes.txt', mimeType: 'text/plain', buffer: bytes } },
      })
      expect(uploaded.status()).toBe(202)
      const resource = await uploaded.json() as { id: string }
      const resourcePath = `/api/v1/course-resources/${resource.id}`
      await expect.poll(async () => {
        const response = await request.get(new URL(resourcePath, appUrl).toString(), { headers: owner.headers })
        expect(response.status()).toBe(200)
        const metadata = await response.json()
        expect(metadata.aiApproved).toBe(false)
        return metadata.status
      }, { timeout: 60_000, intervals: [1000, 2000] }).toBe('AVAILABLE')
      for (const actor of [owner, member]) {
        const downloaded = await request.get(new URL(`${resourcePath}/content`, appUrl).toString(), { headers: actor.headers })
        expect(downloaded.status()).toBe(200)
        expect((await downloaded.body()).equals(bytes)).toBe(true)
      }
      await signIn(page, owner.email, owner.password)

      for (const target of [
        { kind: 'RESOURCE', id: resource.id, path: resourcePath, closedPath: `${resourcePath}/content`, closedStatus: 404, heading: 'Course file deletion' },
        { kind: 'STUDY_SERVER', id: server.id, path: `/api/v1/study-servers/${server.id}`, closedPath: `/api/v1/study-servers/${server.id}/navigation`, closedStatus: 410, heading: 'Study Server deletion' },
      ]) {
        const deleted = await request.delete(new URL(target.path, appUrl).toString(), { headers: owner.headers })
        expect(deleted.status()).toBe(202)
        const accepted = await deleted.json() as { jobId: string; targetId: string; state: string }
        expect(accepted.targetId).toBe(target.id)
        expect(accepted.state).toBe('PENDING')
        expect(/^[a-f0-9-]{36}$/.test(accepted.jobId)).toBe(true)
        const statusPath = `/api/v1/auth/account/source-deletions/${accepted.jobId}`
        // SOURCE_REQUEST is durably dispatched before auth can read it. This proves
        // registered status/reload, not dialog-to-registration timing. No interception.
        await expect.poll(async () => {
          const response = await request.get(new URL(statusPath, appUrl).toString(), { headers: owner.headers })
          expect([200, 404]).toContain(response.status())
          if (response.status() !== 200) return false
          const job = await response.json()
          expect(job.targetKind).toBe(target.kind)
          expect(job.targetId).toBe(target.id)
          expect(['ERASING', 'WAITING_FOR_REPLICA']).toContain(job.state)
          return true
        }, { timeout: 30_000, intervals: [500, 1000, 2000] }).toBe(true)
        const retry = await request.delete(new URL(target.path, appUrl).toString(), { headers: owner.headers })
        expect(retry.status()).toBe(202)
        expect((await retry.json()).jobId).toBe(accepted.jobId)
        const denied = await request.get(new URL(target.closedPath, appUrl).toString(), { headers: member.headers })
        expect(denied.status()).toBe(target.closedStatus)
        if (target.kind === 'RESOURCE') {
          const metadata = await request.get(new URL(target.path, appUrl).toString(), { headers: owner.headers })
          expect(metadata.status()).toBe(404)
        }
        const stranger = await request.get(new URL(statusPath, appUrl).toString(), { headers: member.headers })
        expect(stranger.status()).toBe(404)
        expect((await anonymous.get(statusPath)).status()).toBe(401)
        await page.goto(new URL(`/app/deletions/${accepted.jobId}`, appUrl).toString())
        await expect(page.getByRole('heading', { level: 1, name: target.heading })).toBeVisible()
        await expect(page.getByRole('region', { name: 'Current deletion status' })).toContainText(/Cleanup in progress|Recovery acknowledgement pending/)
        await page.getByRole('button', { name: 'Refresh status' }).click()
        await expect(page.getByRole('button', { name: 'Refresh status' })).toBeEnabled()
        await page.reload()
        await expect(page.getByRole('heading', { level: 1, name: target.heading })).toBeVisible()
        await expect(page.getByRole('region', { name: 'Current deletion status' })).toContainText(/Cleanup in progress|Recovery acknowledgement pending/)
        expect(await page.getByText('Deletion completed', { exact: true }).count()).toBe(0)
      }
    } finally { await anonymous.dispose() }
  })
})
