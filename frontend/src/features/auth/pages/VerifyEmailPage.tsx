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
      setError('Verification token missing.')
      return
    }
    void verifyEmail(token)
      .then((result) => setMessage(result.message))
      .catch((caught: unknown) => {
        setError(caught instanceof Error ? caught.message : 'Unable to verify email')
      })
  }, [token])

  return (
    <main className="v2-auth-page compact-auth">
      <section className="v2-auth-panel solo">
        <div className="v2-auth-card">
          <V2Brand to="/" className="v2-auth-brand" />
          <h1>Verify email</h1>
          {error ? <p role="alert" className="v2-auth-error">{error}</p> : null}
          {message ? <p role="status" className="v2-auth-info">{message}</p> : null}
          {!error && !message ? <p>Verifying…</p> : null}
          <Link className="auth-back" to="/sign-in">Continue to sign in</Link>
        </div>
      </section>
    </main>
  )
}
