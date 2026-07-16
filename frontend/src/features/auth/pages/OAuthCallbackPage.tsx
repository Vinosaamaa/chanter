import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { completeGoogleOauth } from '../auth-api'
import { useAuthStore } from '../../../stores/auth-store'
import { V2Brand } from '../../v2-shell/components/V2Brand'

export function OAuthCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const code = params.get('code')
  const state = params.get('state')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!code || !state) {
      return
    }
    let cancelled = false
    void completeGoogleOauth(code, state)
      .then((session) => {
        if (cancelled) return
        setSession(session)
        navigate('/app/home', { replace: true })
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        setError(caught instanceof Error ? caught.message : 'OAuth sign-in failed')
      })
    return () => {
      cancelled = true
    }
  }, [code, state, navigate, setSession])

  const displayError = code && state ? error : 'OAuth code or state missing.'

  return (
    <main className="v2-auth-page compact-auth">
      <section className="v2-auth-panel solo">
        <div className="v2-auth-card">
          <V2Brand to="/" className="v2-auth-brand" />
          <h1>Signing in…</h1>
          {displayError ? (
            <>
              <p role="alert" className="v2-auth-error">{displayError}</p>
              <Link className="auth-back" to="/sign-in">Back to sign in</Link>
            </>
          ) : (
            <p>Completing Google sign-in.</p>
          )}
        </div>
      </section>
    </main>
  )
}
