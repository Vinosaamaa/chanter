import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../../lib/api-client'
import { V2Brand } from '../v2-shell/components/V2Brand'
import './moderation.css'

export function AppealPage() {
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token'))
  const [restrictionId, setRestriction] = useState(() => new URLSearchParams(window.location.search).get('restriction') ?? '')
  const [email, setEmail] = useState('')
  const [body, setBody] = useState('')
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    if (token) window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
  }, [token])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true); setError('')
    try {
      await apiFetch(token ? '/api/v1/auth/moderation-appeals' : '/api/v1/auth/moderation-appeals/request', {
        method: 'POST', skipAuthRefresh: true,
        body: JSON.stringify(token ? { token, body } : { email, restrictionId }),
      })
      setSaved(true)
    } catch {
      setError(token ? 'Your appeal could not be confirmed. Try again, or request a new link if this one has expired.' : 'The link request could not be confirmed. Please try again.')
    } finally { setBusy(false) }
  }

  return <main className="v2-auth-page compact-auth">
    <section className="v2-auth-panel solo"><div className="v2-auth-card">
      <V2Brand to="/" className="v2-auth-brand" />
      <h1>Review a restriction</h1>
      <p className="auth-lede">{token ? 'Explain what the review should consider. An operator will review your appeal.' : 'Use the verified email on your account and the reference in your restriction notice. You can appeal while your account is suspended.'}</p>
      {saved ? <p role="status" className="v2-auth-info">{token ? 'Your appeal was saved. We will email you after the review.' : 'If this verified email owns the restriction, an appeal link will arrive shortly. The link expires after 20 minutes.'}</p> : <form className="v2-auth-form" onSubmit={submit}>
        {token ? <label>Why should this be reviewed?<textarea className="moderation-appeal-text" value={body} onChange={event => setBody(event.target.value)} required maxLength={4000} /></label> : <>
          <label>Account email<input type="email" value={email} onChange={event => setEmail(event.target.value)} required autoComplete="email" /></label>
          <label>Restriction reference<input value={restrictionId} onChange={event => setRestriction(event.target.value)} required maxLength={36} pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" /></label>
        </>}
        {error && <p role="alert" className="v2-auth-error">{error}</p>}
        <button disabled={busy} type="submit">{busy ? 'Sending…' : token ? 'Send appeal' : 'Send appeal link'}</button>
      </form>}
      {token && !saved && <Link className="auth-back" reloadDocument to="/appeal">Request a new link</Link>}
      <Link className="auth-back" to="/sign-in">Back to sign in</Link>
    </div></section>
  </main>
}
