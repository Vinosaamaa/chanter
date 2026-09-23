import { expect, test, type BrowserContext, type Page, type TestInfo } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFileSync } from 'node:fs'
import { createHmac } from 'node:crypto'

const origin = 'http://127.0.0.1:9419'
const password = process.env.DEMO_PASSWORD ?? 'chanter-dev-demo'
const ownerEmail = 'dev-demo-owner@chanter.local'
const learnerEmail = 'dev-demo-learner@chanter.local'
const reportReason = 'Hosted moderation proof: review this preserved message and its author.'
type Session = { accessToken: string }
type Media = { serverUrl: string; participantToken: string }
type Stats = { sent: number; received: number; energy: number; connected: boolean; remoteParticipants: number }
const transfers: Record<string, { file: string; encodedBytes: number; decodedBytes: number }[]> = {}
declare global {
  interface Window {
    moderationAudio: { connect(url: string, token: string, publish: boolean): Promise<void>; stats(): Promise<Stats>; disconnect(): Promise<void> }
    moderationSocket: { open: boolean; closed: boolean; code: number }
  }
}

// Deliberately outside @product: run only after the ordinary signed-in journeys.
// Never retain traces, videos, cookies, authentication responses or factor setup pixels.
test.use({ baseURL: origin, trace: 'off', video: 'off', screenshot: 'off', actionTimeout: 15000,
  launchOptions: { args: ['--autoplay-policy=no-user-gesture-required'] } })
test.describe.configure({ retries: 0 })
test('real moderation, active audio revocation and verified-email appeal', async ({ browser }, info) => {
  test.skip(process.env.PLAYWRIGHT_MODERATION !== '1', 'Hosted combined product fixture required')
  test.setTimeout(240000)
  const manifest = JSON.parse(readFileSync('dist/.vite/manifest.json', 'utf8')) as Record<string, { file: string; css?: string[] }>
  const owned = Object.entries(manifest).filter(([name]) => /features\/moderation\/(SafetyPage|OperatorPage|AppealPage)\.tsx$/.test(name))
  expect(owned).toHaveLength(3)
  const accountData = manifest['src/features/account-data/AccountDataPage.tsx']
  expect(accountData).toBeDefined()
  const deferred = new Set([...owned.map(([, item]) => item), accountData].flatMap(item => [item.file, ...(item.css ?? [])]))
  const network: Record<string, string[]> = {}
  const contexts: BrowserContext[] = []
  const pageErrors: string[] = []
  const newPage = async () => {
    const context = await browser.newContext({ baseURL: origin, viewport: { width: 1280, height: 900 } })
    contexts.push(context)
    const page = await context.newPage()
    // Only count errors. Browser exception messages can include sensitive request arguments.
    page.on('pageerror', () => pageErrors.push('Uncaught browser exception'))
    return page
  }
  try {
    for (const [name, path] of [['landing', '/'], ['sign-in', '/sign-in'], ['home', '/app/home']]) {
      const page = await newPage()
      if (name === 'home') {
        await signIn(page, ownerEmail)
        // A new context with only the real refresh cookie produces a fresh Home navigation.
        const fresh = await browser.newContext({ baseURL: origin, storageState: await page.context().storageState() })
        contexts.push(fresh)
        await page.close()
        const home = await fresh.newPage()
        home.on('pageerror', () => pageErrors.push('Uncaught browser exception'))
        await measure(home, path, name, network)
        await expect(home.getByRole('heading', { level: 1, name: /^Good / })).toBeVisible()
        await home.close()
      } else { await measure(page, path, name, network); await page.close() }
      expect(network[name].filter(file => deferred.has(file))).toEqual([])
    }
    const operator = await newPage()
    const learner = await newPage()
    const ownerSession = await signIn(operator, ownerEmail)
    const learnerSession = await signIn(learner, learnerEmail)
    const learnerId = JSON.parse(Buffer.from(learnerSession.accessToken.split('.')[1], 'base64url').toString()).sub as string
    const learnerHeaders = bearer(learnerSession)
    const deniedOperator = await learner.request.get('/api/v1/platform-admin/verification', { headers: learnerHeaders })
    expect(deniedOperator.status()).toBe(403)
    const servers = await get<Array<{ id: string; name: string }>>(operator, '/api/v1/study-servers', ownerSession)
    const server = servers.find(item => item.name === 'Workable Product Demo')!
    expect(server).toBeDefined()
    const nav = await get<{ studyServerChannels: Array<{ id: string; kind: string }>; courses: Array<{ cohorts: Array<{ id: string }> }> }>(learner, `/api/v1/study-servers/${server.id}/navigation`, learnerSession)
    const voice = nav.studyServerChannels.find(item => item.kind === 'VOICE')!
    const cohort = nav.courses.flatMap(course => course.cohorts)[0]
    expect(cohort).toBeDefined()
    // Default announcements are instructor-only. Create a normal discussion in this learner's actual cohort.
    const createdChannel = await operator.request.post(`/api/v1/cohorts/${cohort.id}/channels`, {
      headers: bearer(ownerSession), data: { name: 'moderation-proof', kind: 'TEXT' },
    })
    expect(createdChannel.status()).toBe(201)
    const textChannel = await createdChannel.json() as { id: string }
    expect(voice).toBeDefined(); expect(textChannel).toBeDefined()
    const posted = await learner.request.post(`/api/v1/course-channels/${textChannel.id}/messages`, {
      headers: learnerHeaders, data: { body: 'Synthetic moderation evidence. Preserve this message during review.' },
    })
    expect(posted.status()).toBe(201)
    const message = await posted.json() as { id: string }
    await measure(operator, `/app/safety?type=MESSAGE&id=${message.id}`, 'safety', network)
    await operator.getByLabel('Reason for reporting').fill(reportReason)
    const reportSaved = operator.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/moderation/reports' && response.request().method() === 'POST')
    await operator.getByRole('button', { name: 'Send report', exact: true }).click()
    const savedResponse = await reportSaved
    expect(savedResponse.status()).toBe(201)
    const report = await savedResponse.json() as { id: string }
    await expect(operator.getByText('Your report was saved for review.')).toBeVisible()
    await operator.reload()
    await expect(operator.getByText(reportReason, { exact: true })).toBeVisible()
    await pixels(operator, info, 'safety')

    await measure(operator, '/operator', 'operator', network)
    await operator.getByLabel('Account password').fill(password)
    await operator.getByRole('button', { name: 'Set up authenticator' }).click()
    const secret = await operator.locator('.moderation-notice code').innerText()
    await operator.getByLabel('Authenticator code').fill(totp(secret))
    await operator.getByRole('button', { name: 'Verify operator access', exact: true }).click()
    await expect(operator.getByLabel('Investigation reason')).toBeVisible()
    await expect(operator.locator('.moderation-notice code')).toHaveCount(0)
    await operator.getByLabel('Investigation reason').fill('Review hosted report and verify current media enforcement')
    await operator.getByLabel('Report reason or reference').fill(report.id)
    await operator.getByRole('button', { name: 'Load reports', exact: true }).click()
    await operator.getByRole('complementary', { name: 'Report queue' }).getByRole('button').filter({ hasText: reportReason }).click()
    await expect(operator.getByRole('region', { name: 'Selected report' }).getByText('Synthetic moderation evidence. Preserve this message during review.', { exact: true })).toBeVisible()
    await pixels(operator, info, 'operator-case')
    await info.attach('moderation-route-network', { contentType: 'application/json',
      body: Buffer.from(JSON.stringify({ network, transfers }, null, 2)) })

    const receiver = await newPage()
    const sender = await newPage()
    await receiver.goto('/moderation-proof/audio.html'); await sender.goto('/moderation-proof/audio.html')
    const mediaPath = `/api/v1/study-server-channels/${voice.id}/media-token`
    for (const [page, session] of [[operator, ownerSession], [learner, learnerSession]] as const) {
      expect((await page.request.post(`/api/v1/study-server-channels/${voice.id}/voice-presences`, { headers: bearer(session) })).ok()).toBe(true)
    }
    const receiveToken = await post<Media>(operator, mediaPath, ownerSession)
    const sendToken = await post<Media>(learner, mediaPath, learnerSession)
    expect(new URL(sendToken.serverUrl).origin).toBe(origin.replace('http:', 'ws:'))
    expect(await receiver.evaluate(async media => {
      try { await window.moderationAudio.connect(media.serverUrl, media.participantToken, false); return { connected: true } }
      catch (error) { const failure = error as { reason?: unknown; status?: unknown }; return { connected: false,
        reason: typeof failure.reason === 'number' ? failure.reason : null, status: typeof failure.status === 'number' ? failure.status : null } }
    }, receiveToken)).toEqual({ connected: true })
    expect(await sender.evaluate(async media => {
      try { await window.moderationAudio.connect(media.serverUrl, media.participantToken, true); return { connected: true } }
      catch (error) { const failure = error as { reason?: unknown; status?: unknown }; return { connected: false,
        reason: typeof failure.reason === 'number' ? failure.reason : null, status: typeof failure.status === 'number' ? failure.status : null } }
    }, sendToken)).toEqual({ connected: true })
    await expect.poll(async () => (await sender.evaluate(() => window.moderationAudio.stats())).sent, { timeout: 20000 }).toBeGreaterThan(0)
    await expect.poll(async () => (await receiver.evaluate(() => window.moderationAudio.stats())).energy, { timeout: 20000 }).toBeGreaterThan(0)
    const before = await receiver.evaluate(() => window.moderationAudio.stats())
    expect(before.received).toBeGreaterThan(0)
    await sender.evaluate(token => {
      window.moderationSocket = { open: false, closed: false, code: 0 }
      const socket = new WebSocket(`${location.origin.replace('http:', 'ws:')}/api/v1/realtime/ws`, ['chanter-jwt', token])
      socket.onopen = () => { window.moderationSocket.open = true }
      socket.onclose = event => { window.moderationSocket.closed = true; window.moderationSocket.code = event.code }
    }, learnerSession.accessToken)
    await expect.poll(() => sender.evaluate(() => window.moderationSocket.open)).toBe(true)
    // Playwright's URL filter excludes secure 127.0.0.1 cookies on HTTP, unlike Chromium's loopback handling.
    const refreshCookie = (await learner.context().cookies())
      .find(cookie => cookie.name === 'chanter_refresh' && cookie.domain === '127.0.0.1' && cookie.path === '/api/v1/auth')
    expect(Boolean(refreshCookie?.value)).toBe(true)
    expect(refreshCookie?.secure).toBe(true)
    expect(refreshCookie?.httpOnly).toBe(true)

    const target = operator.getByRole('region', { name: 'Selected report' }).getByRole('combobox', { name: /^Target/ })
    await target.selectOption({ label: 'Author account' })
    await expect(target).toHaveValue(`USER:${learnerId}`)
    await operator.getByLabel('Public action reason').fill('Synthetic safety review: account access paused pending appeal.')
    await operator.getByLabel('Confirm target reference', { exact: false }).fill(learnerId)
    const restricted = operator.waitForResponse(response => new URL(response.url()).pathname.endsWith(`/reports/${report.id}/restrictions`) && response.request().method() === 'POST')
    await operator.getByRole('button', { name: 'Apply restriction', exact: true }).click()
    const restrictionResponse = await restricted
    expect(restrictionResponse.ok()).toBe(true)
    const restriction = (restrictionResponse.request().postDataJSON() as { operationId: string }).operationId
    await expect(operator.getByText('Restriction saved.', { exact: true })).toBeVisible()
    await expect.poll(() => sender.evaluate(() => window.moderationAudio.stats()), { timeout: 30000 }).toMatchObject({ connected: false })
    await expect.poll(() => receiver.evaluate(() => window.moderationAudio.stats()), { timeout: 10000 }).toMatchObject({ remoteParticipants: 0 })
    await expect.poll(() => sender.evaluate(() => window.moderationSocket.closed), { timeout: 15000 }).toBe(true)
    expect(await sender.evaluate(() => window.moderationSocket.code)).toBe(1008)
    // The receiver remains connected; removal, not stopping the receiving client, halts media.
    const after = await receiver.evaluate(() => window.moderationAudio.stats())
    expect(after.connected).toBe(true)
    await new Promise(resolve => setTimeout(resolve, 1500))
    const stable = await receiver.evaluate(() => window.moderationAudio.stats())
    expect(stable).toMatchObject({ connected: true, remoteParticipants: 0 })
    expect(stable.received).toBe(after.received)
    const claim = JSON.parse(Buffer.from(sendToken.participantToken.split('.')[1], 'base64url').toString()) as { exp: number }
    expect(claim.exp * 1000).toBeGreaterThan(Date.now())
    // A fresh signaling HTTP request carrying that still-valid signed token must fail at Caddy's guard.
    const reconnectStatus = await sender.evaluate(async token => {
      try { return (await fetch(`/livekit/rtc?access_token=${encodeURIComponent(token)}&protocol=16`)).status } catch { return 0 }
    }, sendToken.participantToken)
    expect(reconnectStatus).toBe(403)
    const reconnect = await sender.evaluate(token => new Promise<{ opened: boolean; closed: boolean; timedOut: boolean }>(resolve => {
      let opened = false
      const socket = new WebSocket(`${location.origin.replace('http:', 'ws:')}/livekit/rtc?access_token=${encodeURIComponent(token)}&protocol=16`)
      const timer = setTimeout(() => { socket.close(); resolve({ opened, closed: false, timedOut: true }) }, 10000)
      socket.onopen = () => { opened = true; socket.close() }
      socket.onerror = () => { /* The denial is paired with the explicit HTTP 403 above. */ }
      socket.onclose = () => { clearTimeout(timer); resolve({ opened, closed: true, timedOut: false }) }
    }), sendToken.participantToken)
    expect(reconnect).toEqual({ opened: false, closed: true, timedOut: false })
    expect((await learner.request.post(mediaPath, { headers: learnerHeaders })).status()).toBe(403)
    expect((await learner.request.get('/api/v1/study-servers', { headers: learnerHeaders })).status()).toBe(403)
    // The rendered app may already have cleared its rejected cookie. Replay the actual issued credential.
    expect((await learner.request.post('/api/v1/auth/refresh', { headers: { Origin: origin, 'X-Chanter-CSRF': '1',
      Cookie: `chanter_refresh=${refreshCookie!.value}` } })).status()).toBe(401)
    await info.attach('moderation-media-evidence', { contentType: 'application/json',
      body: Buffer.from(JSON.stringify({ before, after, stable, activeMediaRemoved: true, liveSessionClosed: true,
        signedUnexpiredReconnectStatus: reconnectStatus, reconnect, issuedRefreshRejected: true }, null, 2)) })
    await receiver.evaluate(() => window.moderationAudio.disconnect())

    // Restriction notices carry a non-secret reference; only the delivered appeal link grants submission.
    const notice = await emailText(learner, 'Chanter moderation restriction', restriction)
    expect(notice.includes(`restriction=${restriction}`)).toBe(true)
    const appeal = await newPage()
    await measure(appeal, `/appeal?restriction=${restriction}`, 'appeal', network)
    await appeal.getByLabel('Account email').fill(learnerEmail)
    await pixels(appeal, info, 'appeal-request')
    await appeal.getByRole('button', { name: 'Send appeal link' }).click()
    await expect(appeal.getByRole('status')).toContainText('an appeal link will arrive shortly')
    const delivered = await emailText(appeal, 'Review your Chanter restriction', restriction)
    const link = delivered.match(/http:\/\/127\.0\.0\.1:9419\/appeal#token=[A-Za-z0-9_-]+/)?.[0]
    expect(Boolean(link)).toBe(true)
    // A delivered email link opens a fresh route; avoid a same-document hash-only navigation.
    await appeal.goto('/')
    await appeal.evaluate(url => { location.assign(url) }, link!)
    await expect(appeal.getByLabel('Why should this be reviewed?')).toBeVisible()
    expect(await appeal.evaluate(() => location.pathname === '/appeal' && location.hash === '')).toBe(true)
    await appeal.getByLabel('Why should this be reviewed?').fill('This is the hosted acceptance fixture. Please reverse the temporary restriction.')
    await pixels(appeal, info, 'appeal-submit')
    await appeal.getByRole('button', { name: 'Send appeal', exact: true }).click()
    await expect(appeal.getByRole('status')).toContainText('Your appeal was saved.')
    const admin = operator.getByRole('region', { name: 'Administrator tools' })
    await admin.getByRole('button', { name: 'Review appeals', exact: true }).click()
    await admin.getByRole('button', { name: 'Load appeals', exact: true }).click()
    await admin.getByRole('button', { name: 'Review appeal', exact: true }).click()
    await admin.getByRole('combobox', { name: /^Decision/ }).selectOption('REVERSED')
    await admin.getByLabel('Decision reason sent to the account').fill('Hosted review complete. Restore access; preserve the evidence and audit history.')
    await admin.getByLabel('Confirm restriction reference', { exact: false }).fill(restriction!)
    await pixels(operator, info, 'operator-appeal')
    await admin.getByRole('button', { name: 'Save appeal decision' }).click()
    await expect(admin.getByRole('status')).toContainText('Appeal resolution saved.')
    await signIn(learner, learnerEmail)
    await expect(learner.getByRole('heading', { level: 1, name: /^Good / })).toBeVisible()
    for (const route of ['safety', 'operator', 'appeal']) expect(network[route].some(file => deferred.has(file))).toBe(true)
    expect(pageErrors).toEqual([])
    await info.attach('moderation-evidence', { contentType: 'application/json', body: Buffer.from(JSON.stringify({
      source: 'Real isolated PostgreSQL, Redis, auth/community/realtime services, Caddy and LiveKit',
      bootstrap: 'Explicit non-web command; fixture account only', network, transfers, before, after, stable,
      activeMediaRemoved: true, liveSessionClosed: true, signedUnexpiredReconnectStatus: reconnectStatus, reconnect,
      verifiedEmailAppealSavedAndReversed: true,
    }, null, 2)) })
  } finally { await Promise.allSettled(contexts.map(context => context.close())) }
})

function bearer(session: Session) { return { Authorization: `Bearer ${session.accessToken}`, Origin: origin, 'X-Chanter-CSRF': '1' } }
async function signIn(page: Page, email: string): Promise<Session> {
  await page.goto('/sign-in')
  const response = page.waitForResponse(item => new URL(item.url()).pathname === '/api/v1/auth/login' && item.request().method() === 'POST')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  const result = await response
  expect(result.status()).toBe(200)
  await expect(page).toHaveURL(/\/app\/home/, { timeout: 30000 })
  return result.json()
}
async function get<T>(page: Page, path: string, session: Session): Promise<T> {
  const response = await page.request.get(path, { headers: bearer(session) }); expect(response.ok()).toBe(true); return response.json()
}
async function post<T>(page: Page, path: string, session: Session): Promise<T> {
  const response = await page.request.post(path, { headers: bearer(session) }); expect(response.ok()).toBe(true); return response.json()
}
async function measure(page: Page, path: string, name: string, network: Record<string, string[]>) {
  const files = new Set<string>()
  const capture = (request: import('@playwright/test').Request) => { const path = new URL(request.url()).pathname; if (path.startsWith('/assets/')) files.add(path.slice(1)) }
  page.on('request', capture)
  await page.goto(path)
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()
  await page.waitForLoadState('networkidle')
  page.off('request', capture); network[name] = [...files].sort()
  transfers[name] = await page.evaluate(() => performance.getEntriesByType('resource')
    .map(item => item as PerformanceResourceTiming)
    .filter(item => new URL(item.name).pathname.startsWith('/assets/'))
    .map(item => ({ file: new URL(item.name).pathname.slice(1), encodedBytes: item.encodedBodySize, decodedBytes: item.decodedBodySize })))
}
async function pixels(page: Page, info: TestInfo, name: string) {
  for (const width of [1280, 390]) {
    await page.setViewportSize({ width, height: 900 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true)
    const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
    expect(audit.violations.map(item => ({ id: item.id, impact: item.impact }))).toEqual([])
    await info.attach(`${name}-${width}`, { body: await page.screenshot({ fullPage: true }), contentType: 'image/png' })
    if (name === 'safety') {
      await page.getByRole('heading', { name: 'Blocked accounts', exact: true }).scrollIntoViewIfNeeded()
      await info.attach(`${name}-blocked-${width}`, { body: await page.screenshot({ fullPage: true }), contentType: 'image/png' })
      await page.getByRole('heading', { level: 1 }).scrollIntoViewIfNeeded()
    }
  }
  await page.setViewportSize({ width: 1280, height: 900 })
}
function totp(secret: string) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  const bits = [...secret].map(character => alphabet.indexOf(character).toString(2).padStart(5, '0')).join('')
  const key = Buffer.from(bits.match(/.{8}/g)!.map(byte => parseInt(byte, 2)))
  const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30000)))
  const digest = createHmac('sha1', key).update(counter).digest(); const offset = digest[digest.length - 1] & 15
  return String((digest.readUInt32BE(offset) & 0x7fffffff) % 1000000).padStart(6, '0')
}
async function emailText(page: Page, subject: string, restriction: string) {
  let text = ''
  await expect.poll(async () => {
    const response = await page.request.get('http://127.0.0.1:8025/api/v1/messages?limit=100')
    expect(response.ok()).toBe(true)
    const inbox = await response.json() as { messages: Array<{ ID: string; Subject: string; To: Array<{ Address: string }> }> }
    for (const mail of inbox.messages.filter(item => item.Subject === subject && item.To.some(to => to.Address === learnerEmail))) {
      const detail = await (await page.request.get(`http://127.0.0.1:8025/api/v1/message/${encodeURIComponent(mail.ID)}`)).json() as { Text: string }
      if (detail.Text.includes(`Restriction reference: ${restriction}`)) { text = detail.Text; return true }
    }
    return false
  }, { timeout: 30000, intervals: [500, 1000] }).toBe(true)
  return text
}
