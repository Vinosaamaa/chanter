import { expect, test } from '@playwright/test'

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
] as const

for (const width of [390, 1280]) {
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
      if (name === 'inbox' && width === 390) await page.locator('.inbox-thread-list button').first().click()
      await page.screenshot({ path: testInfo.outputPath(`fixture-ui-${name}-${width}.png`) })
      expect(pageErrors).toEqual([])
      expect(apiFailures, 'Each route must load its intended fixture instead of an accidental error state').toEqual([])
      expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), 'No document overflow').toBe(false)
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
