import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

// A synthetic socket supplies connection acknowledgements for layout-only screenshots.
// Message persistence, delivery and calls are covered by separate real-backend journeys.
test.beforeEach(async ({ page }) => {
  await page.routeWebSocket('**/api/v1/realtime/ws', socket => {
    socket.onMessage(message => {
      const frame = JSON.parse(String(message))
      if (frame.type === 'subscribe') socket.send(JSON.stringify({ type: 'subscribed', channelId: frame.channelId, channelScope: frame.channelScope }))
    })
  })
})

// Layout evidence uses explicit synthetic responses; authenticated backend journeys run separately.
const course = '/app/servers/visual-study/courses/visual-course-0'
const community = '/app/servers/visual-study/community'
const routes = [
  ['course-overview', `${course}/overview`],
  ['course-chat', `${course}/chat`],
  ['course-questions', `${course}/questions`],
  ['course-resources', `${course}/resources`],
  ['course-people', `${course}/people`],
  ['office-hours', `${course}/office-hours`],
  ['community', `${community}/announcements`],
  ['community-events', `${community}/events`],
  ['community-members', `${community}/members`],
  ['friends', '/app/friends?friend=visual-peer'],
  ['inbox', '/app/inbox'],
  ['calendar', '/app/calendar'],
  ['teaching', '/app/teaching?visual=staff'],
  ['settings', '/app/settings/billing?visual=staff'],
  ['landing', '/'],
  ['sign-in', '/sign-in'],
  ['forgot-password', '/forgot-password'],
  ['terms', '/terms'],
  ['privacy', '/privacy'],
  ['join-or-create', '/app/onboarding/join-or-create'],
  ['create-study-server', '/app/onboarding/create-study-server'],
  ['welcome', '/app/welcome'],
] as const

for (const width of [390, 768, 1280]) {
  for (const [name, route] of routes) {
    test(`fixture UI ${name} at ${width}`, async ({ page }, testInfo) => {
      const apiFailures: string[] = []
      const pageErrors: string[] = []
      page.on('response', response => {
        if (response.url().includes('/api/') && response.status() >= 400) apiFailures.push(`${response.status()} ${new URL(response.url()).pathname}`)
      })
      page.on('pageerror', error => pageErrors.push(error.message))
      await page.setViewportSize({ width, height: 900 })
      await page.goto(route)
      await page.waitForLoadState('networkidle')
      await page.evaluate(() => document.fonts.ready)
      if (name === 'course-questions' && width === 390) await page.locator('.question-thread-list > button').first().click()
      if (name === 'inbox' && width === 390) await page.locator('.inbox-thread-list button').first().click()
      if (name === 'course-chat') await expect(page.locator('.chat-composer')).toBeInViewport()
      await page.screenshot({ path: testInfo.outputPath(`fixture-ui-${name}-${width}.png`) })
      expect(pageErrors).toEqual([])
      expect(apiFailures, 'Each route must load its intended fixture instead of an accidental error state').toEqual([])
      expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), 'No document overflow').toBe(false)
      if (width === 1280 && ['sign-in','course-overview','course-questions','community','friends','calendar','settings','terms'].includes(name)) {
        const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()
        expect(violations.map(({ id, nodes }) => ({ id, targets: nodes.map(node => node.target) }))).toEqual([])
      }
    })
  }
}

test('fixture UI phone landscape composer and keyboard drawer', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 844, height: 390 })
  await page.goto(`${course}/chat`)
  await page.waitForLoadState('networkidle')
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-chat-landscape-844.png') })
  await expect(page.locator('.chat-composer')).toBeInViewport()
})


test('fixture UI phone Inbox marks a notification done and returns to the list', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/app/inbox')
  await page.locator('.inbox-thread-list button').first().click()
  const saved = page.waitForResponse(response => response.url().endsWith('/visual-notification/done') && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Mark done', exact: true }).click()
  expect((await saved).status()).toBe(200)
  await expect(page.locator('.inbox-thread-pane')).toBeVisible()
  await expect(page.locator('.inbox-thread-list')).not.toContainText('A fresh starting point for this week')
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-inbox-completed-390.png') })
})

test('fixture UI phone Questions returns from its reading pane', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${course}/questions`)
  await page.locator('.question-thread-list > button').first().click()
  await expect(page.locator('.question-detail-pane')).toBeVisible()
  await expect(page.locator('.questions-list-pane')).toBeHidden()
  await page.getByRole('button', { name: 'Back to questions' }).click()
  await expect(page.locator('.questions-list-pane')).toBeVisible()
  await expect(page.locator('.question-thread-list > button').first()).toBeFocused()
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-question-list-390.png') })
})


test('fixture UI keeps a reconnecting chat readable', async ({ page }, testInfo) => {
  await page.routeWebSocket('**/api/v1/realtime/ws', socket => socket.close({ code: 1013, reason: 'Synthetic reconnect state' }))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${course}/chat`)
  await expect(page.getByText('Reconnecting to live messages…')).toBeVisible()
  await expect(page.locator('.chat-composer')).toBeInViewport()
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-chat-reconnecting-390.png') })
})

test.describe('200 percent equivalent layout', () => {
  test.use({ viewport: { width: 640, height: 450 }, deviceScaleFactor: 2 })
  test('fixture UI reflows sign-in and Home under reduced motion', async ({ page }, testInfo) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/sign-in')
    await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('fixture-ui-sign-in-zoom-equivalent.png') })
    await page.goto('/app/home')
    await expect(page.getByRole('heading', { name: 'Continue learning' })).toBeVisible()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.locator('#main-content')).toBeFocused()
    await page.screenshot({ path: testInfo.outputPath('fixture-ui-home-zoom-equivalent.png') })
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
  })
})
