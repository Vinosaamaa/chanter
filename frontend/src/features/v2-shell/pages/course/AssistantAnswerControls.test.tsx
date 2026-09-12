import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchAssistantModels } from '../../../questions/questions-api'
import { AssistantAnswerControls } from './AssistantAnswerControls'

vi.mock('../../../questions/questions-api', () => ({ fetchAssistantModels: vi.fn() }))

const catalog = {
  defaultModelId: 'approved-provider',
  models: [
    { id: 'source-only', label: 'Approved sources', provider: 'none', model: 'none', mode: 'sources', billing: 'no-provider-charge' },
    { id: 'approved-provider', label: 'Course quotation model', provider: 'openai', model: 'model-one', mode: 'api', billing: 'separate-api-billing' },
  ],
  answerModes: [
    { id: 'source-only', label: 'Approved sources', available: true, unavailableReason: null },
    { id: 'quoted-evidence', label: 'Relevant source quotations', available: true, unavailableReason: null },
    { id: 'grounded-explanation', label: 'Grounded study explanation', available: false, unavailableReason: 'grounding-evaluation-pending' },
  ],
} as const

function show(overrides = {}) {
  const onInvoke = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AssistantAnswerControls channelId="questions-1" userId="learner-1" isInvoking={false} requiresSourceRecovery={false} onInvoke={onInvoke} {...overrides} /></QueryClientProvider>)
  return onInvoke
}

describe('AssistantAnswerControls', () => {
  afterEach(cleanup)
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(fetchAssistantModels).mockResolvedValue(structuredClone(catalog) as unknown as Awaited<ReturnType<typeof fetchAssistantModels>>) })

  it('uses the authorized default and sends model plus quotation mode only on explicit invocation', async () => {
    const user = userEvent.setup()
    const onInvoke = show()
    expect(await screen.findByRole('combobox', { name: 'Answer source' })).toHaveValue('approved-provider')
    expect(screen.getByRole('combobox', { name: 'Answer type' })).toHaveValue('quoted-evidence')
    expect(screen.getByText(/separate API billing.*chat subscription/i)).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Grounded study explanation/ })).toBeDisabled()
    expect(screen.getByText(/study explanations are not available yet/i)).toBeInTheDocument()
    expect(onInvoke).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Find source quotations' }))
    expect(onInvoke).toHaveBeenCalledWith({ modelId: 'approved-provider', answerMode: 'quoted-evidence' })
  })

  it('keeps source-only selection free of an incompatible quotation mode', async () => {
    const user = userEvent.setup()
    const onInvoke = show()
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Answer source' }), 'source-only')
    expect(screen.getByRole('combobox', { name: 'Answer type' })).toHaveValue('source-only')
    expect(screen.getByRole('option', { name: 'Relevant source quotations' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Find approved sources' }))
    expect(onInvoke).toHaveBeenCalledWith({ modelId: 'source-only', answerMode: 'source-only' })
  })

  it('offers only an explicit source-only recovery after an uncertain provider attempt', async () => {
    const user = userEvent.setup()
    const onInvoke = show({ requiresSourceRecovery: true })
    await screen.findByText(/a previous answer request may have reached the provider/i)
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(onInvoke).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Use approved sources' }))
    expect(onInvoke).toHaveBeenCalledWith({ modelId: 'source-only', answerMode: 'source-only' })
  })

  it('does not offer an invocation when the catalog is unauthorized', async () => {
    vi.mocked(fetchAssistantModels).mockRejectedValue(new Error('forbidden'))
    const onInvoke = show()
    expect(await screen.findByText(/answer options are unavailable/i)).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Find/ })).not.toBeInTheDocument()
    expect(onInvoke).not.toHaveBeenCalled()
  })
})
