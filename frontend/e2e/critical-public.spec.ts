import { expect, test } from '@playwright/test'

/**
 * CI-stable critical subset (@critical). Does not require the Java product stack.
 * Full product paths live in product-critical.spec.ts (@product).
 */
test.describe('Critical public surfaces @critical', () => {
  test('landing loads and CTA routes to sign-in', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByText('Chanter').first()).toBeVisible()
    await page.getByRole('link', { name: 'Sign in' }).first().click()
    await expect(page).toHaveURL(/sign-in/)
  })

  test('sign-in exposes forgot password and terms', async ({ page }) => {
    await page.goto('/sign-in')
    await expect(page.locator('form.v2-auth-form button[type="submit"]')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password')
    await expect(page.getByRole('link', { name: 'Terms' })).toHaveAttribute('href', '/terms')
  })

  test('forgot password page is reachable', async ({ page }) => {
    await page.goto('/forgot-password')
    await expect(page.getByRole('heading', { name: 'Forgot password' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Send reset link' })).toBeVisible()
  })
})

test.describe('Responsive smoke @critical @viewport', () => {
  test('sign-in first viewport stays usable', async ({ page }) => {
    await page.goto('/sign-in')
    const signIn = page.locator('form.v2-auth-form button[type="submit"]')
    await expect(signIn).toBeVisible()
    const box = await signIn.boundingBox()
    expect(box).not.toBeNull()
    expect((box?.width ?? 0)).toBeGreaterThan(40)
  })
})
