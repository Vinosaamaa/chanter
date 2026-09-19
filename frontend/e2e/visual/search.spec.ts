import { expect, test } from '@playwright/test'

for (const width of [390, 768, 1280]) {
  test(`automatic search types at ${width}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/app/servers/visual-study/community/announcements')
    await expect(page.getByRole('heading', { name: 'Announcements', exact: true })).toBeVisible()
    await page.keyboard.press('Control+k')
    const dialog = page.getByRole('dialog', { name: 'Global search' })
    await dialog.getByRole('textbox').fill('study')
    await expect(dialog.getByText('How should I start a study session?')).toBeVisible()
    await expect(dialog.getByRole('heading', { name: /^Messages$/i })).toBeVisible()
    await expect(dialog.getByText('Results update automatically. Only content you can access appears.')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-search-types-${width}.png`) })
    await dialog.getByRole('combobox', { name: 'Content' }).selectOption('ANNOUNCEMENT')
    await expect(dialog.getByText('A fresh starting point for this week')).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-search-announcement-${width}.png`) })
    await dialog.getByText('A fresh starting point for this week').click()
    await expect(dialog).toHaveCount(0)
    await expect(page).toHaveURL(/\/community\/announcements$/)
  })
}
