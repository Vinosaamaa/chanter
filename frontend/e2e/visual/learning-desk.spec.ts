import { expect, test } from '@playwright/test'

// Safe synthetic accounts served only by vite.visual.config.ts. These images prove UI layout, not backend behavior.
for (const width of [360, 390, 768, 1280, 1920, 3840]) {
  for (const state of ['populated', 'empty']) {
    test(`fixture UI Home ${state} at ${width}`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: width > 2000 ? 1440 : 900 })
      await page.goto(`/app/home?visual=${state}`)
      await expect(page.getByRole('heading', { name: 'Continue learning' })).toBeVisible()
      if (state === 'populated') await expect(page.getByRole('heading', { name: 'CS 101 — Foundations of computer science' })).toBeVisible()
      else await expect(page.getByRole('link', { name: 'Join or create a Study Server' })).toBeVisible()
      await page.evaluate(() => document.fonts.ready)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
      expect(overflow, 'The document must not overflow horizontally').toBe(false)
      await page.screenshot({ path: testInfo.outputPath(`fixture-ui-home-${state}-${width}.png`) })
      if (width === 390 && state === 'populated') {
        await page.getByRole('button', { name: 'Browse', exact: true }).click()
        await expect(page.getByRole('dialog', { name: 'Browse Chanter' })).toBeVisible()
        await expect(page.getByRole('button', { name: 'Close navigation', exact: true })).toBeFocused()
        await page.screenshot({ path: testInfo.outputPath('fixture-ui-navigation-390.png') })
        await page.keyboard.press('Escape')
        await expect(page.getByRole('button', { name: 'Browse', exact: true })).toBeFocused()
      }
    })
  }
}
