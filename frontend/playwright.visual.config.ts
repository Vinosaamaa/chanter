import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e/visual',
  outputDir: './test-results/visual-review',
  fullyParallel: true,
  workers: 2,
  retries: 0,
  timeout: 30_000,
  reporter: [['list']],
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } },
    ...(['firefox', 'webkit'] as const).map(browserName => ({ name: browserName, use: { browserName }, grep: /AI |Home (populated|empty) at (390|1280)\b|course-chat at 390|course-overview at 1280|phone Questions|phone Inbox|phone Friends|phone landscape|sign-in at 390|clipped course tabs|mobile marketing menu/ })),
  ],
  use: { baseURL: 'http://127.0.0.1:4174', browserName: 'chromium', timezoneId: 'UTC', locale: 'en-US', trace: 'off', video: 'off', screenshot: 'off' },
  webServer: { command: 'npx vite --config vite.visual.config.ts', url: 'http://127.0.0.1:4174', reuseExistingServer: !process.env.CI },
})
