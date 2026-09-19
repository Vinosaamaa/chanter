import { useEffect, useId, useRef, useState } from 'react'
import { checkNativeConnection, fetchNativeConfiguration, issueNativePairing, parseNativePairing, type NativePairing } from '../../../questions/native-companion-api'

type Props = { disabled: boolean; onInvoke: (pairing: NativePairing, model: string) => void }

export function NativeAnswerControls({ disabled, onInvoke }: Props) {
  const id = useId(), active = useRef<AbortController | null>(null)
  const [available, setAvailable] = useState(false), [busy, setBusy] = useState(false)
  const [installation, setInstallation] = useState(''), [ticket, setTicket] = useState(''), [input, setInput] = useState('')
  const [pairing, setPairing] = useState<NativePairing | null>(null), [models, setModels] = useState<string[]>([])
  const [model, setModel] = useState(''), [approved, setApproved] = useState(false)
  const [message, setMessage] = useState('Use a separately installed Windows companion to connect an eligible Codex account. Web answer options above do not need it.')
  const windows = /Windows/i.test(navigator.userAgent) && !/Android|Mobile/i.test(navigator.userAgent)
  useEffect(() => () => active.current?.abort(), [])
  useEffect(() => {
    if (!pairing) return
    const timer = setTimeout(() => { setPairing(null); setModels([]); setMessage('Native pairing expired. Pair again to continue.'); }, Math.max(0, pairing.expiresAt - Date.now()))
    return () => clearTimeout(timer)
  }, [pairing])
  const run = (operation: (signal: AbortSignal) => Promise<void>) => {
    active.current?.abort()
    const controller = new AbortController(); active.current = controller; setBusy(true)
    void operation(controller.signal).catch((error: unknown) => {
      if (!controller.signal.aborted) { setModels([]); setMessage(error instanceof Error ? error.message : 'Native connection unavailable.') }
    }).finally(() => { if (!controller.signal.aborted) setBusy(false) })
  }
  if (!windows) return <p className="assistant-capability-note">Native subscription access requires the Windows companion. Use the web answer options on this device.</p>
  return <details className="assistant-answer-setup assistant-native-setup">
    <summary>Connect my Codex account on this computer</summary>
    <p role="status">{message}</p>
    {!available ? <button type="button" className="v2-outline-button" disabled={busy || disabled} onClick={() => run(async (signal) => {
      const config = await fetchNativeConfiguration(signal)
      if (!config.available || config.origin !== window.location.origin) { setMessage('Native access is unavailable on this deployment. Use the web answer options above.'); return }
      setAvailable(true); setMessage('Start the installed companion in its visible terminal. Enter the installation ID it prints. Pairing shares your Chanter identity with that local app.')
    })}>Check native availability</button> : <fieldset disabled={busy || disabled} style={{ gridTemplateColumns: 'minmax(0,1fr)' }}>
      <legend>Local connection</legend>
      <label htmlFor={`${id}-installation`}>Companion installation ID</label>
      <input id={`${id}-installation`} value={installation} maxLength={36} onChange={(event) => {
        setInstallation(event.target.value.trim()); setTicket(''); setPairing(null); setModels([]); setInput('')
      }} autoComplete="off" />
      <button type="button" className="v2-outline-button" onClick={() => run(async (signal) => {
        const issued = await issueNativePairing(installation, signal)
        setTicket(`pair ${issued.ticket}`); setPairing(null); setModels([])
        setMessage('Paste this command into the companion terminal within two minutes. Review its identity prompt and type the approval challenge there. Then paste its JSON pairing result below.')
      })}>Create pairing command</button>
      {ticket ? <>
        <label htmlFor={`${id}-command`}>Terminal pairing command</label>
        <textarea id={`${id}-command`} readOnly value={ticket} rows={3} onFocus={(event) => event.currentTarget.select()} />
        <label htmlFor={`${id}-result`}>Pairing result from the terminal</label>
        <textarea id={`${id}-result`} value={input} maxLength={256} rows={2} autoComplete="off" onChange={(event) => { setInput(event.target.value); setModels([]); setPairing(null) }} />
        <button type="button" className="v2-outline-button" onClick={() => run(async (signal) => {
          const next = parseNativePairing(installation, input), offered = await checkNativeConnection(next, signal)
          setPairing(next); setModels(offered); setModel(offered[0]); setApproved(false)
          setMessage('Connected for up to five minutes. Your provider account stays in the companion. Review each question in its terminal before generation.')
        })}>Check connection</button>
      </> : null}
      {pairing && models.length ? <>
        <label htmlFor={`${id}-model`}>Subscription model</label>
        <select id={`${id}-model`} value={model} onChange={(event) => setModel(event.target.value)}>
          {models.map((value) => <option key={value}>{value}</option>)}
        </select>
        <label style={{ display: 'flex', alignItems: 'flex-start' }}><input type="checkbox" style={{ flexShrink: 0, marginTop: 4 }} checked={approved} onChange={(event) => setApproved(event.target.checked)} />
          Send this question and its approved course passages to Codex through my native companion. This uses my provider account limits.</label>
        <p>Find source quotations only. Usage returned by the companion is a client report. A started request cannot be retried with another provider.</p>
        <button type="button" className="v2-primary-button" disabled={!approved || busy || disabled} onClick={() => { setApproved(false); onInvoke(pairing, model) }}>Find quotations with my Codex account</button>
      </> : null}
    </fieldset>}
  </details>
}
