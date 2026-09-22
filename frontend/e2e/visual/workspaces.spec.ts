import AxeBuilder from '@axe-core/playwright'
import { expect, test, type WebSocketRoute } from '@playwright/test'
import { VISUAL_NOW } from './workspace-fixtures'

// A synthetic socket supplies connection acknowledgements for layout-only screenshots.
// Message persistence, delivery and calls are covered by separate real-backend journeys.
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date(VISUAL_NOW))
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

for (const viewport of [{ width: 390, height: 844 }, { width: 1280, height: 900 }, { width: 844, height: 390 }]) {
  test(`fixture UI community dialogs support keyboard navigation at ${viewport.width} @dialogs`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    await page.goto(`${community}/events?visual=staff`)
    const event = page.getByRole('button', { name: 'Community study circle: making difficult ideas clear' })
    await event.focus()
    await page.keyboard.press('Enter')
    const detail = page.getByRole('dialog', { name: 'Community study circle: making difficult ideas clear' })
    await expect(detail.getByRole('button', { name: 'Close event details' })).toBeFocused()
    await event.evaluate(element => element.focus())
    await expect(event).not.toBeFocused()
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-event-details-${viewport.width}.png`) })
    await page.keyboard.press('Escape')
    await expect(detail).toBeHidden()
    await expect(event).toBeFocused()
    for (const [button, name] of [['Create event', 'Create event'], ['Invite people', 'Invite people']]) {
      const opener = page.locator(button === 'Create event' ? '.community-event-toolbar' : '.community-hub-chrome').getByRole('button', { name: button, exact: true })
      await opener.click()
      const dialog = page.getByRole('dialog', { name, exact: true })
      await expect(dialog).toBeVisible()
      expect(await dialog.evaluate(element => element.contains(document.activeElement))).toBe(true)
      await opener.evaluate(element => element.focus())
      await expect(opener).not.toBeFocused()
      await page.screenshot({ path: testInfo.outputPath(`fixture-ui-${button.replaceAll(' ', '-')}-${viewport.width}.png`) })
      await page.keyboard.press('Escape')
      await expect(dialog).toBeHidden()
      await expect(opener).toBeFocused()
    }
    await page.goto(`${community}/announcements?visual=staff`)
    const publish = page.getByRole('button', { name: 'Publish', exact: true })
    await publish.click()
    const announcement = page.getByRole('dialog', { name: 'Publish announcement' })
    await expect(announcement.getByRole('textbox', { name: 'Title', exact: true })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-announcement-editor-${viewport.width}.png`) })
    await page.keyboard.press('Escape')
    await expect(announcement).toBeHidden()
    await expect(publish).toBeFocused()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
  })
}

test('fixture UI legacy teaching bookmark preserves Study Server and actions', async ({ page }) => {
  await page.goto('/app/instructor-dashboard?serverId=visual-study&visual=staff')
  await expect(page).toHaveURL(/\/app\/teaching\?serverId=visual-study&visual=staff/)
  await expect(page.getByRole('button', { name: 'Refresh teaching' })).toBeVisible()
  await expect(page.getByText('Low-confidence handoffs', { exact: true })).toBeVisible()
  await expect(page.getByText('Office Hours waitlist', { exact: true })).toBeVisible()
})

const routes = [
  ['course-overview', `${course}/overview`],
  ['course-chat', `${course}/chat`],
  ['course-questions', `${course}/questions`],
  ['course-resources', `${course}/resources`],
  ['course-people', `${course}/people`],
  ['office-hours', `${course}/office-hours`],
  ['community', `${community}/announcements`],
  ['community-lounge', `${community}/lounge`],
  ['discover-courses', `${community}/discover`],
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

for (const width of [360, 390, 768, 1280, 1920, 3840]) {
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
      if (name === 'course-questions' && width <= 390) await page.locator('.question-thread-list > button').first().click()
      if (name === 'inbox' && width <= 390) await page.locator('.inbox-thread-list button').first().click()
      await page.screenshot({ path: testInfo.outputPath(`fixture-ui-${name}-${width}.png`) })
      if (name === 'course-chat' || name === 'community-lounge') await expect(page.locator('.chat-composer')).toBeInViewport()
      expect(pageErrors).toEqual([])
      expect(apiFailures, 'Each route must load its intended fixture instead of an accidental error state').toEqual([])
      expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), 'No document overflow').toBe(false)
      if (width === 1280) {
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

test('fixture UI exposes clipped course tabs on a phone', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${course}/overview`)
  await page.getByRole('button', { name: 'More course workspace tabs' }).click()
  await expect(page.getByRole('link', { name: 'People', exact: true })).toBeInViewport()
  await page.getByRole('link', { name: 'People', exact: true }).click()
  await expect(page.getByRole('link', { name: 'People', exact: true })).toHaveAttribute('aria-current', 'page')
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-course-phone-tabs.png') })
})

for (const size of [{ width: 390, height: 844 }, { width: 844, height: 390 }, { width: 1280, height: 900 }]) {
  test(`fixture UI device sessions at ${size.width}`, async ({ page }, testInfo) => {
    await page.setViewportSize(size)
    await page.goto('/app/home')
    if (size.width <= 900) await page.getByRole('button', { name: 'Open navigation' }).click()
    await page.getByRole('button', { name: 'Open account menu' }).click()
    await page.getByRole('menuitem', { name: 'Sessions and devices' }).click()
    const dialog = page.getByRole('dialog', { name: 'Sessions and devices' })
    await expect(dialog.getByText('Chrome on Windows')).toBeVisible()
    await expect(dialog.getByText(/up to 15 minutes/)).toBeVisible()
    const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()
    expect(violations.map(({ id }) => id)).toEqual([])
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-device-sessions-${size.width}.png`) })
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('button', { name: 'Open account menu' })).toBeFocused()
  })
}


test('fixture UI hides the mobile marketing menu after resizing to desktop', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByRole('button', { name: 'Open navigation' }).click()
  const menu = page.getByRole('navigation', { name: 'Mobile marketing' })
  await expect(menu).toBeVisible()
  await page.setViewportSize({ width: 1280, height: 900 })
  await expect(menu).toBeHidden()
  await expect(page.getByRole('navigation', { name: 'Marketing', exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-marketing-menu-resized-1280.png') })
})

test('fixture UI scrolls a long device session list in phone landscape', async ({ page }, testInfo) => {
  const timestamp = '2026-09-12T04:14:00Z'
  await page.route('**/api/v1/auth/sessions', route => route.fulfill({ json: { sessions: Array.from({ length: 12 }, (_, index) => ({
    id: `visual-device-${index}`, createdAt: timestamp, lastUsedAt: timestamp, expiresAt: '2026-09-19T04:14:00Z',
    userAgent: index === 11 ? 'Safari/605.1.15 (iPhone)' : 'Chrome/140.0.0.0 (Windows NT 10.0)', current: index === 0,
  })) } }))
  await page.setViewportSize({ width: 844, height: 390 })
  await page.goto('/app/home')
  await page.getByRole('button', { name: 'Open navigation' }).click()
  await page.getByRole('button', { name: 'Open account menu' }).click()
  await page.getByRole('menuitem', { name: 'Sessions and devices' }).click()
  const dialog = page.getByRole('dialog', { name: 'Sessions and devices' })
  const lastDevice = dialog.getByText('Safari on iPhone')
  await expect(lastDevice).toBeVisible()
  await lastDevice.scrollIntoViewIfNeeded()
  await expect(lastDevice).toBeInViewport()
  expect(await dialog.evaluate(element => element.scrollTop)).toBeGreaterThan(0)
  await dialog.getByText(/up to 15 minutes/).scrollIntoViewIfNeeded()
  await expect(dialog.getByText(/up to 15 minutes/)).toBeInViewport()
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-device-sessions-long-landscape.png') })
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
})

test('fixture UI phone Friends stays on the list after Back and reload', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/app/friends?friend=visual-peer')
  await expect(page.getByRole('heading', { name: 'Alexandra Montgomery-Williams' })).toBeFocused()
  await page.getByRole('button', { name: 'Back to friends' }).click()
  await expect(page.locator('.friends-list-pane').getByRole('button', { name: /Alexandra Montgomery-Williams/ })).toBeFocused()
  await expect(page).toHaveURL(/\/app\/friends$/)
  await page.reload()
  await expect(page.locator('.friends-list-pane')).toBeVisible()
  await expect(page.locator('.dm-pane')).toBeHidden()
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-friends-back-reload-390.png') })
  await page.locator('.friends-list-pane').getByRole('button', { name: /Alexandra Montgomery-Williams/ }).click()
  await expect(page.getByRole('heading', { name: 'Alexandra Montgomery-Williams' })).toBeFocused()
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-friends-conversation-focus-390.png') })
  await expect(page).toHaveURL(/friend=visual-peer$/)
  await page.reload()
  await expect(page.locator('.dm-pane')).toBeVisible()
  await expect(page.locator('.friends-list-pane')).toBeHidden()
})

test('fixture UI phone Friends dialog contains focus and restores Add friend', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/app/friends')
  const opener = page.getByRole('button', { name: 'Add friend', exact: true })
  await opener.click()
  const dialog = page.getByRole('dialog', { name: 'Add a friend' })
  await expect(page.getByRole('textbox', { name: 'Search co-members' })).toBeFocused()
  await opener.evaluate(element => element.focus())
  await expect(opener).not.toBeFocused()
  for (let index = 0; index < 8; index++) {
    await page.keyboard.press('Tab')
    // Native dialogs keep the document behind them inert; browsers may still
    // move focus to their own chrome, reported here as document.body.
    expect(await dialog.evaluate(element => document.activeElement === document.body || element.contains(document.activeElement))).toBe(true)
  }
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-friend-dialog-390.png') })
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(opener).toBeFocused()
})

test('fixture UI phone Inbox marks a notification done and returns to the list', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/app/inbox')
  await page.locator('.inbox-thread-list button').first().click()
  await expect(page.getByRole('heading', { name: 'A fresh starting point for this week' })).toBeFocused()
  const saved = page.waitForResponse(response => response.url().endsWith('/visual-notification/done') && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Mark done', exact: true }).click()
  expect((await saved).status()).toBe(200)
  await expect(page.locator('.inbox-thread-pane')).toBeVisible()
  await expect(page.locator('.inbox-thread-list')).not.toContainText('A fresh starting point for this week')
  await expect(page.locator('.inbox-thread-list').getByRole('button', { name: /Alexandra mentioned you/ })).toBeFocused()
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-inbox-completed-390.png') })
})

test('fixture UI phone Inbox restores its heading when the last notification is completed', async ({ page }) => {
  await page.route(/\/api\/v1\/me\/notifications(?:\?.*)?$/, async route => {
    const response = await route.fetch()
    const data = await response.json() as { notifications: Array<{ id: string }> }
    await route.fulfill({ response, json: { ...data, notifications: data.notifications.filter(item => item.id === 'visual-notification') } })
  })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/app/inbox')
  await page.locator('.inbox-thread-list button').click()
  await page.getByRole('button', { name: 'Mark done', exact: true }).click()
  await expect(page.getByText('No open notifications.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Inbox', exact: true })).toBeFocused()
})

test('fixture UI phone Inbox reports failed completion and permits retry', async ({ page }) => {
  let attempts = 0
  await page.route('**/api/v1/me/notifications/visual-notification/done', route => {
    attempts += 1
    return attempts === 1 ? route.fulfill({ status: 503, json: { message: 'Synthetic temporary failure' } }) : route.continue()
  })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/app/inbox')
  await page.locator('.inbox-thread-list button').first().click()
  await page.getByRole('button', { name: 'Mark done', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Could not mark this notification done')
  await expect(page.getByRole('heading', { name: 'A fresh starting point for this week' })).toBeVisible()
  await page.getByRole('button', { name: 'Mark done', exact: true }).click()
  await expect(page.locator('.inbox-thread-pane')).toBeVisible()
  await expect(page.locator('.inbox-thread-list')).not.toContainText('A fresh starting point for this week')
  expect(attempts).toBe(2)
})

for (const width of [390, 1280]) {
  test(`fixture UI phone Friends incoming call uses an inert modal at ${width}`, async ({ page }, testInfo) => {
    const sockets: WebSocketRoute[] = []
    const declined: string[] = []
    await page.routeWebSocket('**/api/v1/realtime/ws', socket => {
      sockets.push(socket)
      socket.onMessage(message => {
        const frame = JSON.parse(String(message))
        if (frame.type === 'call_decline') declined.push(frame.callId)
      })
    })
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/app/friends?friend=visual-peer')
    const previous = page.getByRole('button', { name: 'Start voice call with Alexandra Montgomery-Williams' })
    await expect(previous).toBeVisible()
    await expect(previous).toBeEnabled()
    await previous.focus()
    await expect(previous).toBeFocused()
    await expect.poll(() => sockets.length).toBeGreaterThan(0)
    for (const socket of sockets) socket.send(JSON.stringify({ type: 'call_ringing', callId: 'visual-call', callerUserId: 'visual-peer', calleeUserId: 'visual-learner', direction: 'incoming' }))
    const dialog = page.getByRole('dialog', { name: 'Voice call with Alexandra Montgomery-Williams' })
    await expect(dialog.getByRole('button', { name: 'Accept voice call' })).toBeFocused()
    await previous.evaluate(element => element.focus())
    await expect(previous).not.toBeFocused()
    await page.keyboard.press('Escape')
    await expect(dialog).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-incoming-call-${width}.png`) })
    await dialog.getByRole('button', { name: 'Decline voice call' }).click()
    await expect(dialog).toBeHidden()
    // The actual ended phase keeps its launch button disabled briefly.
    await expect(page.getByRole('heading', { name: 'Alexandra Montgomery-Williams' })).toBeFocused()
    expect(declined).toEqual(['visual-call'])
  })
}

test('fixture UI phone Questions returns from its reading pane', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${course}/questions`)
  await page.locator('.question-thread-list > button').first().click()
  await expect(page.locator('.question-detail-pane')).toBeVisible()
  await expect(page.getByRole('region', { name: 'Question conversation' })).toBeFocused()
  await expect(page.locator('.questions-list-pane')).toBeHidden()
  await page.getByRole('button', { name: 'Back to questions' }).click()
  await expect(page.locator('.question-thread-list > button').first()).toBeFocused()
  await expect(page.locator('.questions-list-pane')).toBeVisible()
  await expect(page.locator('.question-thread-list > button').first()).toBeFocused()
  await page.screenshot({ path: testInfo.outputPath('fixture-ui-question-list-390.png') })
})


test('fixture UI keeps a reconnecting chat readable', async ({ page }, testInfo) => {
  await page.routeWebSocket('**/api/v1/realtime/ws', socket => socket.close({ code: 1013, reason: 'Synthetic reconnect state' }))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${course}/chat`)
  await expect(page.getByText(/Reconnecting to live messages/)).toBeVisible()
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
