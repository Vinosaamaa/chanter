import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const jobId = 'b633c892-6762-40ec-a945-b042957a052b'
// Synthetic interaction evidence only; actual source and recovery completion requires #251/#342.
for (const viewport of [{ width: 390, height: 844 }, { width: 844, height: 390 }, { width: 1280, height: 900 }]) {
  for (const kind of ['RESOURCE', 'STUDY_SERVER'] as const) {
    test(`${kind} deletion confirmation and status at ${viewport.width} @accountdata`, async ({ page }, info) => {
      await page.setViewportSize(viewport)
      const resource = kind === 'RESOURCE'
      const targetId = resource ? 'visual-resource' : 'visual-study'
      const targetName = resource ? 'Problem-solving field notes' : 'Open Learning Collective'
      const path = resource ? '/app/servers/visual-study/courses/visual-course-0/resources?visual=staff' : '/app/picker?visual=staff'
      if (resource) await page.route('**/api/v1/study-servers/visual-study/navigation', async route => {
        const response = await route.fetch()
        const navigation = await response.json() as { courses: { id: string; capabilities: Record<string, boolean> }[] }
        const course = navigation.courses.find(item => item.id === 'visual-course-0')!
        course.capabilities.canUploadResources = true
        return route.fulfill({ response, json: navigation })
      })
      let requests = 0
      await page.route(`**/api/v1/${resource ? 'course-resources' : 'study-servers'}/${targetId}`, async route => {
        if (route.request().method() !== 'DELETE') return route.continue()
        expect(route.request().headers().authorization).toBeTruthy()
        requests++
        return route.fulfill({ status: 202, json: { jobId, targetId, state: 'PENDING' } })
      })
      let progressReady = false
      await page.route(`**/api/v1/auth/account/source-deletions/${jobId}`, route => {
        expect(route.request().headers().authorization).toBeTruthy()
        if (!progressReady) return route.fulfill({ status: 404, json: { message: 'Not registered yet' } })
        return route.fulfill({ json: { jobId, targetId, targetKind: kind, state: 'WAITING_FOR_REPLICA', replicationPending: true,
          parts: [{ source: resource ? 'media' : 'community', state: 'PRESERVED', errorCode: null }] } })
      })
      await page.goto(path)
      await expect(page.locator('.v2-app-shell')).toBeVisible()
      const opener = page.getByRole('button', { name: resource ? `Delete ${targetName}` : 'Delete', exact: true })
      await opener.click()
      const dialog = page.getByRole('dialog', { name: `Delete ${targetName}?`, exact: true })
      await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused()
      await page.keyboard.press('Escape')
      await expect(dialog).toBeHidden()
      await expect(opener).toBeFocused()
      expect(requests).toBe(0)
      await opener.click()
      const confirm = dialog.getByRole('button', { name: resource ? 'Delete course file' : 'Delete Study Server', exact: true })
      await confirm.scrollIntoViewIfNeeded()
      await expect(confirm).toBeInViewport()
      expect(await dialog.locator('.create-event-modal').evaluate(element => getComputedStyle(element).backgroundColor)).toBe('rgb(255, 255, 255)')
      await page.screenshot({ path: info.outputPath(`fixture-ui-source-deletion-${kind}-${viewport.width}.png`) })
      if (viewport.width === 1280) expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([])
      await confirm.click()
      await expect(page).toHaveURL(new RegExp(`/app/deletions/${jobId}$`))
      await expect(page.getByRole('alert')).toContainText('Progress is not available yet')
      await expect(page.getByText('Deletion completed', { exact: true })).toHaveCount(0)
      progressReady = true
      await page.getByRole('button', { name: 'Refresh status' }).click()
      await expect(page.getByRole('heading', { name: 'Recovery acknowledgement pending' })).toBeVisible()
      await page.getByText('Service results', { exact: true }).click()
      await expect(page.getByText('Some records retained')).toBeVisible()
      expect(requests).toBe(1)
      expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
      await page.screenshot({ path: info.outputPath(`fixture-ui-source-progress-${kind}-${viewport.width}.png`), fullPage: true })
    })
  }
}
