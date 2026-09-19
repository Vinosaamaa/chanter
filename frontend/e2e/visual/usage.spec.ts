import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { workspaceFixture } from './workspace-fixtures'

// Synthetic layout states. Actual usage, owner tampering and reload run in product-critical.spec.ts.
const route = '/app/settings/usage?visual=staff'
const dashboard = workspaceFixture('/instructor-dashboard', false, true, new Set()) as Record<string, unknown>

for (const width of [360, 768, 1280]) {
  test(`fixture UI free-beta Usage at ${width} @usage`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/app/settings/billing?visual=staff')
    await expect(page).toHaveURL(/\/app\/settings\/usage$/)
    await expect(page.getByRole('heading', { name: 'Free beta' })).toBeVisible()
    await expect(page.getByText('42 of 100 assistant runs used, 58 remaining')).toBeVisible()
    await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '42')
    await expect(page.getByText(/does not reset monthly/)).toBeVisible()
    await expect(page.getByRole('button', { name: /upgrade|checkout|save plan/i })).toHaveCount(0)
    await expect(page.getByText(/invoice|card details|\$29/i)).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: info.outputPath(`fixture-ui-usage-${width}.png`) })
    if (width === 1280) expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([])
    await page.getByRole('link', { name: 'Back to Home', exact: true }).click()
    await expect(page).toHaveURL(/\/app\/home$/)
  })
}

for (const used of [0, 85, 105]) {
  test(`fixture UI Usage truthful ${used} runs on phone @usage`, async ({ page }, info) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.route('**/instructor-dashboard', request => request.fulfill({ json: { ...dashboard, aiInvocationCount: used, remainingAiInvocations: Math.max(0, 100 - used), quotaExhausted: used >= 100 } }))
    await page.goto(route)
    await expect(page.getByText(`${used} of 100 assistant runs used, ${Math.max(0, 100 - used)} remaining`)).toBeVisible()
    await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', String(Math.min(100, used)))
    if (used >= 100) await expect(page.getByText('Assistant run limit reached', { exact: true })).toBeVisible()
    else if (used >= 80) await expect(page.getByText('Assistant run limit nearly reached', { exact: true })).toBeVisible()
    await page.screenshot({ path: info.outputPath(`fixture-ui-usage-count-${used}.png`) })
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
  })
}

test('fixture UI Usage request failure never becomes zero usage @usage', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/instructor-dashboard', request => request.fulfill({ status: 503, json: { message: 'Usage is temporarily unavailable.' } }))
  await page.goto(route)
  await expect(page.getByRole('alert')).toContainText('Usage is temporarily unavailable')
  await expect(page.getByRole('progressbar')).toHaveCount(0)
  await expect(page.getByText(/0 of/)).toHaveCount(0)
  await page.screenshot({ path: info.outputPath('fixture-ui-usage-unavailable.png') })
})
