import {
  expect,
  test as base,
  type Page,
  type Request,
  type TestInfo,
} from '@playwright/test'
import { isExpectedRequestAbort, navigationAbortTracker } from './browser-health.mjs'

type BrowserFailure = {
  kind: 'console' | 'page' | 'request' | 'response'
  detail: string
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
    const responseStatuses = new WeakMap<Request, number>()
    const navigation = navigationAbortTracker()
    const failedRequests: Request[] = []
    page.on('request', request => {
      navigation.started(request, request.isNavigationRequest() && request.frame() === page.mainFrame(), request.redirectedFrom())
    })
    page.on('requestfinished', request => navigation.finished(request))
    page.on('framenavigated', frame => {
      const request = navigation.candidate() as Request | undefined
      if (frame !== page.mainFrame() || !request) return
      const destination = new URL(frame.url())
      destination.hash = ''
      const status = responseStatuses.get(request)
      if (destination.href === request.url() && status !== undefined && status >= 200 && status < 300) navigation.committed(request)
    })

    page.on('console', (message) => {
      if (message.type() === 'error') {
        failures.push({ kind: 'console', detail: message.text() })
      }
    })
    page.on('pageerror', (error) => {
      failures.push({ kind: 'page', detail: error.stack ?? error.message })
    })
    page.on('requestfailed', (request) => {
      navigation.failed(request)
      failedRequests.push(request)
    })
    page.on('response', (response) => {
      // Chromium can report ERR_ABORTED after a successfully received bodyless
      // 204. Require that exact observed status; real network failures still fail.
      responseStatuses.set(response.request(), response.status())
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
    for (const request of failedRequests) {
      if (!isExpectedRequestAbort(request.isNavigationRequest(), request.failure()?.errorText ?? '', responseStatuses.get(request), navigation.wasReplaced(request))) {
        failures.push({
          kind: 'request',
          detail: `${request.method()} ${new URL(request.url()).pathname} (${request.failure()?.errorText ?? 'unknown failure'})`,
        })
      }
    }
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
