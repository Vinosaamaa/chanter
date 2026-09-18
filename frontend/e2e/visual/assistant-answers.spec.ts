import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { VISUAL_NOW } from './workspace-fixtures'

// Synthetic contract responses prove layout and client recovery only. Real source retrieval
// and persistence run separately in assistant-answers.spec.ts against the seeded backend.
const routePath = '/app/servers/visual-study/courses/visual-course-0/questions'
const question = { id: 'visual-own-question', channelMessageId: 'visual-message', channelId: 'visual-questions', senderUserId: 'visual-learner', body: 'How do we choose a useful first step when the problem feels too large?', status: 'UNANSWERED', createdAt: VISUAL_NOW }
const catalog = {
  defaultModelId: 'visual-model',
  models: [
    { id: 'source-only', label: 'Approved sources', provider: 'none', model: 'none', mode: 'sources', billing: 'no-provider-charge' },
    { id: 'visual-model', label: 'Course quotation model', provider: 'example-provider', model: 'example-model', mode: 'api', billing: 'separate-api-billing' },
  ],
  answerModes: [
    { id: 'source-only', label: 'Approved sources', available: true, unavailableReason: null },
    { id: 'quoted-evidence', label: 'Relevant source quotations', available: true, unavailableReason: null },
    { id: 'grounded-explanation', label: 'Grounded study explanation', available: false, unavailableReason: 'grounding-evaluation-pending' },
  ],
}
const answer = {
  id: 'visual-answer', supportQuestionId: question.id, channelId: question.channelId, studyServerId: 'visual-study', learnerUserId: 'visual-learner', questionBody: question.body,
  answerBody: 'Begin by sketching the inputs and outputs, then choose one small step you can check.', confidence: 'HIGH', handoffRecommended: false, supportQuestionStatus: 'AI_ANSWERED',
  sources: [{ resourceId: 'visual-resource', resourceTitle: 'Problem-solving field notes', excerpt: 'Begin by sketching the inputs and outputs, then choose one small step you can check.' }],
  createdAt: VISUAL_NOW, audit: { llmUsed: true, llmProvider: 'saved-provider', llmModel: 'saved-model', sourceCount: 1, invocationType: 'GROUNDED_ANSWER', createdAt: VISUAL_NOW },
}

async function prepare(page: Page) {
  await page.clock.setFixedTime(new Date(VISUAL_NOW))
  await page.route('**/api/v1/course-channels/*/support-questions', route => route.fulfill({ json: { supportQuestions: [question] } }))
  await page.route('**/assistant-models', route => route.fulfill({ json: catalog }))
  await page.route('**/assistant-answer', route => route.fulfill({ json: null }))
  await page.goto(routePath)
  await page.locator('.question-thread-list > button').first().click()
  await expect(page.getByRole('combobox', { name: 'Answer source' })).toBeVisible()
}

for (const size of [{ width: 390, height: 844 }, { width: 768, height: 900 }, { width: 1280, height: 900 }, { width: 844, height: 390 }]) {
  test(`fixture UI AI answer options and saved audit at ${size.width}`, async ({ page }, info) => {
    await page.setViewportSize(size)
    await prepare(page)
    const requests: string[] = []
    await page.route('**/assistant-answer/stream?*', route => {
      requests.push(new URL(route.request().url()).search)
      return route.fulfill({ contentType: 'text/event-stream', body: `event: status\ndata: retrieving\n\nevent: complete\ndata: ${JSON.stringify(answer)}\n\n` })
    })
    await page.getByRole('button', { name: 'Find source quotations' }).scrollIntoViewIfNeeded()
    await page.screenshot({ path: info.outputPath(`fixture-ui-ai-options-${size.width}.png`) })
    const controls = page.locator('.assistant-answer-setup').locator('select,button')
    for (const control of await controls.all()) expect((await control.boundingBox())?.height).toBeGreaterThanOrEqual(44)
    if (size.width === 390) expect(await page.getByLabel('Answer source').evaluate(el => parseFloat(getComputedStyle(el).fontSize))).toBeGreaterThanOrEqual(16)
    await page.getByRole('button', { name: 'Find source quotations' }).click()
    await expect(page.getByText(/saved-provider \/ saved-model/)).toBeVisible()
    await expect(page.locator('.answer-sources')).toContainText('Problem-solving field notes')
    expect(requests).toEqual(['?modelId=visual-model&answerMode=quoted-evidence'])
    await page.getByText(/saved-provider \/ saved-model/).scrollIntoViewIfNeeded()
    await page.screenshot({ path: info.outputPath(`fixture-ui-ai-saved-${size.width}.png`) })
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
    if (size.width === 1280) expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()).violations).toEqual([])
  })
}

for (const terminal of ['eof', 'error'] as const) {
  test(`fixture UI phone AI ${terminal} discards draft and recovers with approved sources`, async ({ page }, info) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await prepare(page)
    const selections: string[] = []
    await page.route('**/assistant-answer/stream?*', route => {
      const params = new URL(route.request().url()).searchParams
      selections.push(params.get('modelId') ?? '')
      const sourceOnly = params.get('modelId') === 'source-only' && params.get('answerMode') === 'source-only'
      const complete = { ...answer, audit: { ...answer.audit, llmUsed: false, llmProvider: 'none', llmModel: 'none' } }
      const failure = terminal === 'error' ? `event: error\ndata: ${JSON.stringify({ code: 'GENERATION_ALREADY_ATTEMPTED', status: 409, message: 'A previous generation may have reached the provider. Use Source only or ask your Instructor / TA.' })}\n\n` : ''
      return route.fulfill({ contentType: 'text/event-stream', body: sourceOnly ? `event: complete\ndata: ${JSON.stringify(complete)}\n\n` : `event: status\ndata: retrieving\n\nevent: token\ndata: UNSAVED PARTIAL DRAFT\n\n${failure}` })
    })
    await page.getByRole('button', { name: 'Find source quotations' }).click()
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByText('UNSAVED PARTIAL DRAFT')).toHaveCount(0)
    await expect(page.locator('.answer-sources')).toHaveCount(0)
    expect(selections).toEqual(['visual-model'])
    await expect(page.getByRole('combobox', { name: 'Answer source' })).toHaveCount(0)
    await page.getByRole('button', { name: 'Use approved sources' }).scrollIntoViewIfNeeded()
    await page.screenshot({ path: info.outputPath(`fixture-ui-ai-${terminal}-recovery-390.png`) })
    await page.getByRole('button', { name: 'Use approved sources' }).click()
    await expect(page.getByText(/Approved sources · no generation model/)).toBeVisible()
    await expect(page.getByRole('region', { name: 'Answer recovery' })).toHaveCount(0)
    await expect(page.getByRole('combobox', { name: 'Answer source' })).toHaveCount(0)
    expect(selections).toEqual(['visual-model', 'source-only'])
    await page.screenshot({ path: info.outputPath(`fixture-ui-ai-${terminal}-recovered-390.png`) })
  })
}

test('fixture UI AI keyboard controls reflow at 200 percent equivalent width', async ({ page }, info) => {
  await page.setViewportSize({ width: 640, height: 450 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await prepare(page)
  const source = page.getByRole('combobox', { name: 'Answer source' })
  await source.focus()
  await page.keyboard.press('Home')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('combobox', { name: 'Answer type' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: 'Find approved sources' })).toBeFocused()
  await expect(page.getByRole('button', { name: 'Find approved sources' })).toBeInViewport()
  await page.screenshot({ path: info.outputPath('fixture-ui-ai-zoom-equivalent.png') })
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
})
