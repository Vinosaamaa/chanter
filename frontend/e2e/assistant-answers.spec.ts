import { expect, test } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173'
test.use({ baseURL, trace: 'off', video: 'off', screenshot: 'off' })

test('learner retrieves approved sources and reloads the authoritative saved answer @product', async ({ page }) => {
  test.skip(!process.env.PLAYWRIGHT_PRODUCT, 'Requires the real seeded product stack')
  const pageErrors: string[] = []
  const failedResponses: { path: string; method: string; status: number }[] = []
  const consoleErrors: { text: string; url: string }[] = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push({ text: message.text(), url: message.location().url }) })
  page.on('response', response => {
    if (response.status() >= 400 && new URL(response.url()).pathname.startsWith('/api/')) failedResponses.push({ path: new URL(response.url()).pathname, method: response.request().method(), status: response.status() })
  })
  await page.goto('/sign-in')
  const login = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/auth/login' && response.request().method() === 'POST')
  await page.getByLabel('Email', { exact: true }).fill(process.env.DEMO_LEARNER_EMAIL ?? 'dev-demo-learner@chanter.local')
  await page.getByLabel('Password', { exact: true }).fill(process.env.DEMO_PASSWORD ?? 'chanter-dev-demo')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  const { accessToken } = await (await login).json() as { accessToken: string }
  const headers = { Authorization: `Bearer ${accessToken}` }
  await expect(page).toHaveURL(/\/app\/home/)
  const servers = await (await page.request.get('/api/v1/study-servers', { headers })).json() as { id: string; name: string }[]
  const server = servers.find(server => server.name === 'Workable Product Demo')!
  expect(server).toBeDefined()
  const nav = await (await page.request.get(`/api/v1/study-servers/${server.id}/navigation`, { headers })).json() as { courses: { id: string; channels: { id: string; name: string }[] }[] }
  const course = nav.courses.find(course => course.channels.some(channel => channel.name === 'questions'))!
  const channel = course.channels.find(channel => channel.name === 'questions')!
  const catalogResponse = await page.request.get(`/api/v1/course-channels/${channel.id}/assistant-models`, { headers })
  expect(catalogResponse.status()).toBe(200)
  const catalog = await catalogResponse.json() as { models: { id: string }[]; answerModes: { id: string; available: boolean }[] }
  expect(catalog.models.some(model => model.id === 'source-only')).toBe(true)
  expect(catalog.answerModes.find(mode => mode.id === 'grounded-explanation')?.available).toBe(false)
  await page.goto(`/app/servers/${server.id}/courses/${course.id}/questions`)
  const created = page.waitForResponse(response => new URL(response.url()).pathname === `/api/v1/course-channels/${channel.id}/support-questions` && response.request().method() === 'POST')
  await page.getByRole('textbox', { name: 'Ask a support question' }).fill(`When should I submit homework assignments through the course portal? Browser check ${Date.now()}`)
  await page.getByRole('button', { name: 'Send question' }).click()
  const question = await (await created).json() as { id: string }
  await expect(page.getByRole('combobox', { name: 'Answer source' })).toBeVisible()
  await page.getByRole('combobox', { name: 'Answer source' }).selectOption('source-only')
  const streamed = page.waitForResponse(response => new URL(response.url()).pathname.endsWith(`/${question.id}/assistant-answer/stream`))
  await page.getByRole('button', { name: 'Find approved sources' }).click()
  expect((await streamed).status()).toBe(200)
  await expect(page.getByText(/Saved answer · Approved sources · no generation model/)).toBeVisible({ timeout: 30_000 })
  await expect(page.locator('.answer-sources')).toContainText('Homework Help Guide')
  const savedResponse = await page.request.get(`/api/v1/course-channels/${channel.id}/support-questions/${question.id}/assistant-answer`, { headers })
  expect(savedResponse.status()).toBe(200)
  const saved = await savedResponse.json() as { id: string; answerBody: string; audit: { llmUsed: boolean }; sources: unknown[] }
  expect(saved.audit.llmUsed).toBe(false)
  expect(saved.sources.length).toBeGreaterThan(0)
  await page.reload()
  await expect(page.locator('.assistant-answer')).toContainText(saved.answerBody)
  await expect(page.getByText(/Approved sources · no generation model/)).toBeVisible()
  // Absence of an answer is the existing GET contract for a newly posted question.
  const missingAnswerPath = `/api/v1/course-channels/${channel.id}/support-questions/${question.id}/assistant-answer`
  expect(failedResponses.filter(response => !(response.status === 404 && response.method === 'GET' && response.path === missingAnswerPath))).toEqual([])
  expect(consoleErrors.filter(error => !(error.text.includes('404') && error.url.endsWith(missingAnswerPath)))).toEqual([])
  expect(pageErrors).toEqual([])
})
