import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e/visual',
  outputDir: './test-results/visual-review',
  fullyParallel: true,
  workers: 2,
  retries: 0,
  timeout: 30_000,
  reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:4174', browserName: 'chromium', trace: 'off', video: 'off', screenshot: 'off' },
  webServer: { command: 'npx vite --config vite.visual.config.ts', url: 'http://127.0.0.1:4174', reuseExistingServer: !process.env.CI },
})
