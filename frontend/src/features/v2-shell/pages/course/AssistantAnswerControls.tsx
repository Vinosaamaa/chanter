import { useId, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { fetchAssistantModels } from '../../../questions/questions-api'
import type { AssistantAnswerMode, AssistantAnswerSelection, AssistantModelCatalog } from '../../../questions/support-question-types'
import './assistant-answer-controls.css'

type Props = {
  channelId: string
  userId: string
  isInvoking: boolean
  requiresSourceRecovery: boolean
  onInvoke: (selection: AssistantAnswerSelection) => void
}

export function AssistantAnswerControls(props: Props) {
  const catalog = useQuery({
    queryKey: ['assistant-models', props.userId, props.channelId],
    queryFn: () => fetchAssistantModels(props.channelId),
    retry: false,
    staleTime: 60_000,
  })
  if (catalog.isPending) return <p className="assistant-options-status" role="status">Loading answer options…</p>
  if (catalog.isError || !catalog.data?.models.length) return (
    <div className="assistant-options-status">
      <p>Answer options are unavailable for this channel. Your Instructor / TA can still help with this question.</p>
      <button type="button" className="v2-outline-button" onClick={() => void catalog.refetch()}>Reload answer options</button>
    </div>
  )
  return <AvailableAnswerControls {...props} catalog={catalog.data} />
}

function AvailableAnswerControls({ catalog, isInvoking, requiresSourceRecovery, onInvoke }: Props & { catalog: AssistantModelCatalog }) {
  const id = useId()
  const [recovering, setRecovering] = useState(false)
  const [selection, setSelection] = useState<AssistantAnswerSelection | null>(null)
  const model = catalog.models.find((entry) => entry.id === selection?.modelId)
    ?? catalog.models.find((entry) => entry.id === catalog.defaultModelId)
    ?? catalog.models[0]
  const canQuote = model.mode !== 'sources' && catalog.answerModes.some((mode) => mode.id === 'quoted-evidence' && mode.available)
  const availableMode = catalog.answerModes.find((mode) => mode.id === selection?.answerMode && mode.available
    && (mode.id !== 'quoted-evidence' || canQuote))
  const mode = availableMode?.id ?? (canQuote ? 'quoted-evidence' : 'source-only')
  const sourceOnly = mode === 'source-only'
  const explanationUnavailable = catalog.answerModes.some((mode) => mode.id === 'grounded-explanation' && !mode.available)
  const safeSourceAvailable = catalog.models.some((model) => model.id === 'source-only')
    && catalog.answerModes.some((mode) => mode.id === 'source-only' && mode.available)

  if (requiresSourceRecovery && (recovering || !isInvoking)) return (
    <section className="assistant-answer-setup assistant-answer-recovery" aria-label="Answer recovery">
      <div>
        <p>A previous answer request may have reached the provider. Use approved sources without another provider request, or ask your Instructor / TA in this question thread.</p>
        {safeSourceAvailable ? <button type="button" className="v2-outline-button" disabled={isInvoking} onClick={() => { setRecovering(true); onInvoke({ modelId: 'source-only', answerMode: 'source-only' }) }}>{isInvoking ? 'Finding approved sources…' : 'Use approved sources'}</button> : null}
      </div>
    </section>
  )

  return (
    <section className="assistant-answer-setup" aria-label="Answer options" aria-describedby={`${id}-description`}>
      <fieldset disabled={isInvoking}>
        <legend className="sr-only">Choose how to use approved course sources</legend>
        <label htmlFor={`${id}-model`}>Answer source
          <select id={`${id}-model`} value={model.id} onChange={(event) => {
            const nextModel = catalog.models.find((model) => model.id === event.target.value)!
            const nextMode = nextModel.mode === 'sources' || !catalog.answerModes.some((mode) => mode.id === 'quoted-evidence' && mode.available) ? 'source-only' : 'quoted-evidence'
            setSelection({ modelId: nextModel.id, answerMode: nextMode })
          }}>
            {catalog.models.map((model) => <option key={model.id} value={model.id}>{model.label}</option>)}
          </select>
        </label>
        <label htmlFor={`${id}-mode`}>Answer type
          <select id={`${id}-mode`} value={mode} onChange={(event) => setSelection({ modelId: model.id, answerMode: event.target.value as AssistantAnswerMode })}>
            {catalog.answerModes.map((mode) => <option key={mode.id} value={mode.id} disabled={!mode.available || (mode.id === 'quoted-evidence' && !canQuote)}>{mode.label}{!mode.available ? ' — unavailable' : ''}</option>)}
          </select>
        </label>
      </fieldset>
      <p id={`${id}-description`}>{sourceOnly
        ? 'Find approved course passages. No generation model or provider charge.'
        : 'Extract relevant quotations from approved course sources. This does not generate a study explanation.'}</p>
      {!sourceOnly ? <p className="assistant-billing-note">{billingDescription(model.billing)}</p> : null}
      {explanationUnavailable ? <p className="assistant-capability-note">Grounded study explanations are not available yet.</p> : null}
      <button type="button" className="v2-primary-button" disabled={isInvoking} onClick={() => onInvoke({ modelId: model.id, answerMode: mode })}>
        <Search size={16} aria-hidden />{isInvoking ? 'Finding sources…' : sourceOnly ? 'Find approved sources' : 'Find source quotations'}
      </button>
    </section>
  )
}

function billingDescription(billing: string): string {
  switch (billing) {
    case 'local-compute': return 'Uses the course’s configured local model and compute.'
    case 'separate-api-billing': return 'Uses the course’s provider account with separate API billing. A chat subscription does not include this usage.'
    case 'provider-account-dependent': return 'Usage is billed under the course’s provider account terms.'
    case 'no-provider-charge': return 'No provider charge.'
    default: return 'Usage follows the course’s configured provider account terms.'
  }
}
