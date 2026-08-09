import {
  expect,
  test as base,
  type Page,
  type Request,
  type TestInfo,
} from '@playwright/test'

type BrowserFailure = {
  kind: 'console' | 'page' | 'request' | 'response'
  detail: string
}

function ignoredNavigationAbort(request: Request) {
  const error = request.failure()?.errorText ?? ''
  return request.isNavigationRequest() && /ERR_ABORTED|NS_BINDING_ABORTED/.test(error)
}

async function attachFailures(testInfo: TestInfo, failures: BrowserFailure[]) {
  if (failures.length === 0) return
  await testInfo.attach('browser-health-failures', {
    body: Buffer.from(JSON.stringify(failures, null, 2)),
    contentType: 'application/json',
  })
}

export const test = base.extend<{ browserHealth: void }>({
  browserHealth: [async ({ page }, use, testInfo) => {
    const failures: BrowserFailure[] = []

    page.on('console', (message) => {
      if (message.type() === 'error') {
        failures.push({ kind: 'console', detail: message.text() })
      }
    })
    page.on('pageerror', (error) => {
      failures.push({ kind: 'page', detail: error.stack ?? error.message })
    })
    page.on('requestfailed', (request) => {
      if (!ignoredNavigationAbort(request)) {
        failures.push({
          kind: 'request',
          detail: `${request.method()} ${request.url()} (${request.failure()?.errorText ?? 'unknown failure'})`,
        })
      }
    })
    page.on('response', (response) => {
      const responseUrl = new URL(response.url())
      const configuredBaseUrl = new URL(
        process.env.PLAYWRIGHT_BASE_URL
          ?? testInfo.project.use.baseURL
          ?? 'http://127.0.0.1',
      )
      if (responseUrl.origin === configuredBaseUrl.origin && response.status() >= 400) {
        failures.push({
          kind: 'response',
          detail: `${response.status()} ${response.request().method()} ${responseUrl.pathname}`,
        })
      }
    })

    await use()
    await attachFailures(testInfo, failures)
    expect(failures, 'critical browser journey emitted runtime or network failures').toEqual([])
  }, { auto: true }],
})

export async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(async () => {
    await document.fonts.ready
    return {
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: Math.max(
        document.documentElement.scrollWidth,
        document.body?.scrollWidth ?? 0,
      ),
    }
  })
  expect(
    dimensions.scrollWidth,
    `document width ${dimensions.scrollWidth}px exceeds viewport width ${dimensions.clientWidth}px`,
  ).toBeLessThanOrEqual(dimensions.clientWidth + 1)
}

export { expect }
