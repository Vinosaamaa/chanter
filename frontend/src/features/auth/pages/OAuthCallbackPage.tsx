import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { completeGoogleOauth } from '../auth-api'
import { useAuthStore } from '../../../stores/auth-store'
import { V2Brand } from '../../v2-shell/components/V2Brand'

export function OAuthCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const code = params.get('code')
    if (!code) {
      setError('OAuth code missing.')
      return
    }
    void completeGoogleOauth(code)
      .then((session) => {
        setSession(session)
        navigate('/app/home', { replace: true })
      })
      .catch((caught: unknown) => {
        setError(caught instanceof Error ? caught.message : 'OAuth sign-in failed')
      })
  }, [navigate, params, setSession])

  return (
    <main className="v2-auth-page compact-auth">
      <section className="v2-auth-panel solo">
        <div className="v2-auth-card">
          <V2Brand to="/" className="v2-auth-brand" />
          <h1>Signing in…</h1>
          {error ? (
            <>
              <p role="alert" className="v2-auth-error">{error}</p>
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
