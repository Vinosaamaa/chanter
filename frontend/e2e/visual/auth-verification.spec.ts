import { expect, test } from '@playwright/test'

// Synthetic provider outage proves browser recovery and layout, not a live Turnstile account.
for (const width of [320, 390, 1280]) {
  test(`auth verification alternative at ${width}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 })
    await page.route('**/api/v1/auth/verification-options', route => route.fulfill({
      json: { enabled: true, siteKey: 'fixture-site-key' },
    }))
    await page.route('https://challenges.cloudflare.com/**', route => route.abort())
    await page.route('**/api/v1/auth/register', route => route.fulfill({ status: 202,
      json: { verificationRequired: true, message: 'Check your email for a verification link.' },
    }))
    await page.route('**/api/v1/auth/forgot-password', route => route.fulfill({ status: 202,
      json: { message: 'If the account exists, a reset link is on its way.' },
    }))
    await page.goto('/sign-in?cohort=fixture-cohort&invite=fixture-invite')
    await page.getByRole('tab', { name: 'Sign in', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeEnabled()
    await page.getByRole('tab', { name: 'Create account' }).click()
    await page.getByLabel('Full name').fill('Sam Lee')
    await page.getByLabel('Email', { exact: true }).fill('learner@example.test')
    await page.getByLabel('Password', { exact: true }).fill('fixture-password-253')
    await expect(page.getByText('Verification could not load. You can continue by email.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create account', exact: true })).toBeDisabled()
    await page.evaluate(() => document.fonts.ready)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-auth-verification-outage-${width}.png`), fullPage: true })
    await page.getByRole('button', { name: 'Use email verification instead' }).click()
    const signupRequest = page.waitForRequest(request => request.url().endsWith('/auth/register'))
    await page.getByRole('button', { name: 'Create account', exact: true }).click()
    expect((await signupRequest).headers()['x-chanter-verification-method']).toBe('email')
    await expect(page.getByText('Check your email for a verification link.')).toBeVisible()
    await expect(page.getByText('After verifying your email, return to this tab to finish joining your cohort.', { exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-auth-invitation-continuation-${width}.png`), fullPage: true })
    await page.getByRole('link', { name: 'Forgot password?' }).click()
    await expect(page.getByRole('heading', { name: 'Forgot password', exact: true })).toBeVisible()
    await page.getByLabel('Email', { exact: true }).fill('learner@example.test')
    await page.getByRole('button', { name: 'Use email verification instead' }).click()
    const recoveryRequest = page.waitForRequest(request => request.url().endsWith('/auth/forgot-password'))
    await page.getByRole('button', { name: 'Send reset link' }).click()
    expect((await recoveryRequest).headers()['x-chanter-verification-method']).toBe('email')
    await expect(page.getByText('If the account exists, a reset link is on its way.')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    await page.screenshot({ path: testInfo.outputPath(`fixture-ui-auth-verification-recovery-${width}.png`), fullPage: true })
  })
}
