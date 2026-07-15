import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { verifyEmail } from '../auth-api'
import { V2Brand } from '../../v2-shell/components/V2Brand'

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      return
    }
    let cancelled = false
    void verifyEmail(token)
      .then((result) => {
        if (!cancelled) setMessage(result.message)
      })
      .catch((caught: unknown) => {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : 'Unable to verify email')
        }
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const displayError = token ? error : 'Verification token missing.'

  return (
    <main className="v2-auth-page compact-auth">
      <section className="v2-auth-panel solo">
        <div className="v2-auth-card">
          <V2Brand to="/" className="v2-auth-brand" />
          <h1>Verify email</h1>
          {displayError ? <p role="alert" className="v2-auth-error">{displayError}</p> : null}
          {message ? <p role="status" className="v2-auth-info">{message}</p> : null}
          {!displayError && !message ? <p>Verifying…</p> : null}
          <Link className="auth-back" to="/sign-in">Continue to sign in</Link>
        </div>
      </section>
    </main>
  )
}
