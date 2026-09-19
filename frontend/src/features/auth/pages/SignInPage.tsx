import { useEffect, useRef, useState } from 'react'
import { Eye, EyeOff, BookOpen, MessageSquare, CalendarDays } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

import { CohortInviteRedirect } from '../components/CohortInviteRedirect'
import { HumanVerificationControl } from '../components/HumanVerificationControl'
import {
  fetchOauthProviders,
  isAuthSession,
  login,
  register,
  type OAuthProvider,
  type HumanVerification,
} from '../auth-api'
import { isHttpOrHttpsUrl } from '../is-http-or-https-url'
import { authenticateBrowserSession, signOutBrowserSession } from '../browser-session'
import { useAuthStore } from '../../../stores/auth-store'
import { readCohortInviteParams } from '../../onboarding/cohort-invite'
import { V2Brand } from '../../v2-shell/components/V2Brand'

type AuthMode = 'sign-in' | 'register'

export function SignInPage() {
  const location = useLocation()
  const accessToken = useAuthStore((state) => state.accessToken)
  const inviteFromUrl = readCohortInviteParams(location.search)
  const [mode, setMode] = useState<AuthMode>(inviteFromUrl ? 'register' : 'sign-in')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [verification, setVerification] = useState<HumanVerification | null>(null)
  const [verificationAttempt, setVerificationAttempt] = useState(0)
  const [logoutRetried, setLogoutRetried] = useState(false)
  const logoutFailed = !logoutRetried && Boolean((location.state as { logoutFailed?: boolean } | null)?.logoutFailed)
  const [oauthProviders, setOauthProviders] = useState<OAuthProvider[]>([])
  const authTabRefs = useRef<Record<AuthMode, HTMLButtonElement | null>>({
    'sign-in': null,
    register: null,
  })

  const defaultRedirect = inviteFromUrl ? '/app/welcome' : '/app/home'
  const redirectTo = (location.state as { from?: string } | null)?.from ?? defaultRedirect

  useEffect(() => {
    void fetchOauthProviders()
      .then((response) => setOauthProviders(response.providers))
      .catch(() => setOauthProviders([]))
  }, [])

  if (accessToken) {
    return <CohortInviteRedirect to={redirectTo} search={location.search} />
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (mode === 'register' && !verification) return
    setIsSubmitting(true)
    setError(null)
    setInfo(null)
    try {
      if (mode === 'sign-in') {
        await authenticateBrowserSession(() => login({ email, password }))
        return
      }
      const result = await authenticateBrowserSession(() => register({ email, password, displayName }, verification ?? undefined))
      if (!isAuthSession(result)) {
        setInfo(result.message)
        setMode('sign-in')
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to authenticate')
    } finally {
      setIsSubmitting(false)
      if (mode === 'register') { setVerification(null); setVerificationAttempt((attempt) => attempt + 1) }
    }
  }

  const googleProvider = oauthProviders.find((provider) => provider.id === 'google')
  const googleAuthorizationUrl =
    googleProvider && isHttpOrHttpsUrl(googleProvider.authorizationUrl)
      ? googleProvider.authorizationUrl
      : null

  const handleAuthTabKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    let nextMode: AuthMode | null = null
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      nextMode = mode === 'sign-in' ? 'register' : 'sign-in'
    } else if (event.key === 'Home') {
      nextMode = 'sign-in'
    } else if (event.key === 'End') {
      nextMode = 'register'
    }
    if (!nextMode) return

    event.preventDefault()
    setMode(nextMode)
    if (nextMode !== mode) setVerification(null)
    authTabRefs.current[nextMode]?.focus()
  }

  return (
    <main className="v2-auth-page">
      <section className="v2-auth-hero">
        <V2Brand to="/" className="v2-auth-brand" />
        <div className="auth-hero-copy">
          <h1>A place for your next question.</h1>
          <p>Come back to your Courses, find your people, and keep learning together.</p>
        </div>
        <div className="auth-learning-map" aria-hidden="true">
          <span><BookOpen /><strong>Your Courses</strong><small>Materials and ideas worth returning to</small></span>
          <span><MessageSquare /><strong>Your conversations</strong><small>Questions, perspectives and shared progress</small></span>
          <span><CalendarDays /><strong>Your time together</strong><small>Office Hours and community events</small></span>
        </div>
      </section>

      <section className="v2-auth-panel">
        <div className="v2-auth-card">
          {logoutFailed ? (
            <div className="v2-auth-error" role="alert">
              <p>You are signed out here, but we could not revoke the browser session. Reconnect and retry sign-out.</p>
              <button type="button" onClick={() => {
                void signOutBrowserSession().then(() => setLogoutRetried(true)).catch(() => setLogoutRetried(false))
              }}>Retry sign-out</button>
            </div>
          ) : null}
          <header className="auth-card-heading">
            <h2>{inviteFromUrl ? 'Join your learning community' : 'Welcome to Chanter'}</h2>
            <p>{inviteFromUrl ? 'Create an account or sign in to accept your Course invitation.' : 'Sign in to pick up where you left off.'}</p>
          </header>


          <div className="v2-auth-tabs" role="tablist" aria-label="Authentication mode">
            <button
              ref={(element) => {
                authTabRefs.current['sign-in'] = element
              }}
              id="auth-tab-sign-in"
              type="button"
              role="tab"
              aria-controls="auth-mode-panel"
              aria-selected={mode === 'sign-in'}
              tabIndex={mode === 'sign-in' ? 0 : -1}
              className={mode === 'sign-in' ? 'active' : undefined}
              onClick={() => { setMode('sign-in'); setVerification(null) }}
              onKeyDown={handleAuthTabKeyDown}
            >
              Sign in
            </button>
            <button
              ref={(element) => {
                authTabRefs.current.register = element
              }}
              id="auth-tab-register"
              type="button"
              role="tab"
              aria-controls="auth-mode-panel"
              aria-selected={mode === 'register'}
              tabIndex={mode === 'register' ? 0 : -1}
              className={mode === 'register' ? 'active' : undefined}
              onClick={() => { if (mode !== 'register') { setMode('register'); setVerification(null) } }}
              onKeyDown={handleAuthTabKeyDown}
            >
              Create account
            </button>
          </div>

          <form
            id="auth-mode-panel"
            className="v2-auth-form"
            role="tabpanel"
            aria-labelledby={`auth-tab-${mode}`}
            onSubmit={handleSubmit}
          >
            {mode === 'register' ? (
              <label>Full name<input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Sam Lee" required autoComplete="name" /></label>
            ) : null}
            <label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" required autoComplete="email" /></label>
            <div className="v2-auth-field">
              <label htmlFor="auth-password">Password</label>
              <span className="password-field">
                <input
                  id="auth-password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="••••••••••••••••"
                  required
                  minLength={8}
                  autoComplete={mode === 'sign-in' ? 'current-password' : 'new-password'}
                />
                <button
                  type="button"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword((current) => !current)}
                >
                  {showPassword ? <EyeOff /> : <Eye />}
                </button>
              </span>
            </div>
            {mode === 'sign-in' ? (
              <p className="auth-forgot"><Link to="/forgot-password">Forgot password?</Link></p>
            ) : null}
            {error ? <p role="alert" className="v2-auth-error">{error}</p> : null}
            {info ? <p role="status" className="v2-auth-info">{info}</p> : null}
            {mode === 'register' ? <HumanVerificationControl key={verificationAttempt} action="register" onChange={setVerification} /> : null}
            <button type="submit" disabled={isSubmitting || (mode === 'register' && !verification)}>{isSubmitting ? 'Working…' : mode === 'register' ? 'Create account' : 'Sign in'}</button>
          </form>

          {googleAuthorizationUrl ? (
            <>
              <div className="auth-divider"><span />or<span /></div>
              <a className="google-button" href={googleAuthorizationUrl}>
                <b aria-hidden="true">G</b> Continue with Google
              </a>
            </>
          ) : null}
          <p className="auth-terms">By continuing you agree to the <Link to="/terms">Terms</Link></p>
          <Link className="auth-back" to="/">Back to Chanter</Link>
        </div>
      </section>
    </main>
  )
}
