import { apiFetch } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import type { AssistantAnswer } from './support-question-types'
import type { StreamAssistantHandlers } from './questions-api'

export type NativeConfiguration = { available: boolean; origin: string; publicKey: string | null; models: string[] }
export type NativePairing = { installationId: string; handle: string; expiresAt: number }
type Ticket = { ticket: string; expiresAt: number }
type NativeResult = { text: string; usage?: { inputTokens: number | null; outputTokens: number | null } }
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const LOCAL = 'http://127.0.0.1:43160'

export function fetchNativeConfiguration(signal?: AbortSignal): Promise<NativeConfiguration> {
  return apiFetch('/api/v1/native-companion/configuration', { signal })
}
export function issueNativePairing(installationId: string, signal?: AbortSignal): Promise<Ticket> {
  if (!UUID.test(installationId)) throw new Error('Enter the installation ID shown by the native companion.')
  return apiFetch('/api/v1/native-companion/pair', { method: 'POST', signal, body: JSON.stringify({ installationId, approved: true }) })
}
export function parseNativePairing(installationId: string, input: string): NativePairing {
  let value: unknown
  try { value = JSON.parse(input) } catch { throw new Error('Paste the pairing result printed by the native companion.') }
  const pair = value as Partial<NativePairing> | null
  if (!UUID.test(installationId) || !pair || typeof pair.handle !== 'string' || !/^[a-zA-Z0-9_-]{43}$/.test(pair.handle)
    || !Number.isSafeInteger(pair.expiresAt) || pair.expiresAt! <= Date.now() || pair.expiresAt! > Date.now() + 300_000) {
    throw new Error('This pairing result is invalid or expired. Pair again in the native companion.')
  }
  return { installationId, handle: pair.handle, expiresAt: pair.expiresAt! }
}

async function localRequest(path: '/status' | '/study', pairing: NativePairing, body: object, signal: AbortSignal): Promise<string> {
  if (pairing.expiresAt <= Date.now()) throw new Error('Native pairing expired. Pair again to continue.')
  const response = await fetch(`${LOCAL}${path}`, { method: 'POST', credentials: 'omit', redirect: 'error', signal,
    headers: { 'Content-Type': 'application/json', 'X-Chanter-Pairing': pairing.handle }, body: JSON.stringify(body) })
  if (!response.ok || !response.body) throw new Error('The native companion rejected this request. Check its terminal status.')
  const reader = response.body.getReader(), decoder = new TextDecoder()
  let text = '', bytes = 0
  try {
    while (true) {
      const chunk = await reader.read()
      if (signal.aborted) throw new DOMException('Native request cancelled.', 'AbortError')
      if (chunk.done) break
      bytes += chunk.value.byteLength
      if (bytes > (path === '/status' ? 64 * 1024 : 512 * 1024)) throw new Error('Native response exceeded its size limit.')
      text += decoder.decode(chunk.value, { stream: true })
    }
    return text + decoder.decode()
  } finally { await reader.cancel().catch(() => undefined); reader.releaseLock() }
}

async function withAccount<T>(signal: AbortSignal | undefined, operation: (signal: AbortSignal) => Promise<T>): Promise<T> {
  const controller = new AbortController(), generation = useAuthStore.getState().generation
  const abort = () => controller.abort()
  signal?.addEventListener('abort', abort, { once: true })
  if (signal?.aborted) abort()
  const unsubscribe = useAuthStore.subscribe((state) => { if (state.generation !== generation) abort() })
  const timeout = setTimeout(abort, 120_000)
  try { return await operation(controller.signal) }
  finally { clearTimeout(timeout); unsubscribe(); signal?.removeEventListener('abort', abort) }
}

export function checkNativeConnection(pairing: NativePairing, signal?: AbortSignal): Promise<string[]> {
  return withAccount(signal, async (ownedSignal) => {
    const config = await fetchNativeConfiguration(ownedSignal)
    if (!config.available || config.origin !== window.location.origin) throw new Error('Native access is unavailable on this deployment. Web answer options are still available.')
    const ticket = await apiFetch<Ticket>('/api/v1/native-companion/status-ticket', { method: 'POST', signal: ownedSignal,
      body: JSON.stringify({ installationId: pairing.installationId }) })
    const status = JSON.parse(await localRequest('/status', pairing, { ticket: ticket.ticket }, ownedSignal))
    if (status?.account?.state !== 'subscription') throw new Error('Sign in through the native companion’s provider-owned login, then check the connection again.')
    const { primary, secondary, spendControlReached } = status.limits ?? {}
    if (![primary, secondary].every((window) => Number.isInteger(window?.usedPercent) && window.usedPercent >= 0 && window.usedPercent < 100)
      || spendControlReached === true) throw new Error('Subscription capacity is unavailable or unknown. Web answer options are still available.')
    const models = Array.isArray(status.models) ? config.models.filter((model) => status.models.some((entry: { model?: unknown }) => entry?.model === model)) : []
    if (!models.length) throw new Error('No model is available in both this deployment and your subscription.')
    return models
  })
}

export async function streamNativeAnswer(channelId: string, questionId: string, pairing: NativePairing, model: string,
  handlers: StreamAssistantHandlers): Promise<void> {
  await withAccount(handlers.signal, async (signal) => {
    // Recheck live session, provider capacity and deployment/model intersection immediately before reservation.
    const models = await checkNativeConnection(pairing, signal)
    if (!models.includes(model)) throw new Error('The selected native model is no longer available.')
    const base = `/api/v1/course-channels/${channelId}/support-questions/${questionId}`
    const issued = await apiFetch<Ticket & { requestId: string; prompt: string }>(`${base}/native-request`, {
      method: 'POST', signal, body: JSON.stringify({ installationId: pairing.installationId, model, exportApproved: true }) })
    const wire = await localRequest('/study', pairing, { ticket: issued.ticket, prompt: issued.prompt }, signal)
    if (!wire.endsWith('\n\n')) throw new Error('The native response ended before its completion frame.')
    let result: NativeResult | undefined
    for (const frame of wire.split('\n\n').filter(Boolean)) {
      const lines = frame.split('\n'), event = lines.find((line) => line.startsWith('event: '))?.slice(7)
      const data = lines.find((line) => line.startsWith('data: '))?.slice(6)
      if (event === 'error') throw new Error('The native request did not complete. Use approved sources or ask your Instructor / TA.')
      if (event === 'completed') {
        if (result || !data) throw new Error('The native response was invalid.')
        result = JSON.parse(data) as NativeResult
      } else if (event !== 'delta') throw new Error('The native response was invalid.')
    }
    if (!result || typeof result.text !== 'string' || new TextEncoder().encode(result.text).length > 8192) throw new Error('The native request ended without a bounded result.')
    // Client output becomes visible only after server-side authorization and quotation validation.
    const answer = await apiFetch<AssistantAnswer>(`${base}/native-results/${issued.requestId}`, { method: 'POST', signal,
      body: JSON.stringify({ installationId: pairing.installationId, text: result.text, usage: result.usage }) })
    if (signal.aborted) throw new DOMException('Native request cancelled.', 'AbortError')
    handlers.onComplete(answer)
  })
}
