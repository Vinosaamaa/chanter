import { useEffect, useState } from 'react'
import { Eye, EyeOff, FileText, HelpCircle, MessageSquare, CalendarDays } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

import { CohortInviteRedirect } from '../components/CohortInviteRedirect'
import {
  fetchOauthProviders,
  isAuthSession,
  login,
  register,
  type OAuthProvider,
} from '../auth-api'
import { isHttpOrHttpsUrl } from '../is-http-or-https-url'
import { useAuthStore } from '../../../stores/auth-store'
import { readCohortInviteParams } from '../../onboarding/cohort-invite'
import { V2Brand } from '../../v2-shell/components/V2Brand'

type AuthMode = 'sign-in' | 'register'

export function SignInPage() {
  const location = useLocation()
  const accessToken = useAuthStore((state) => state.accessToken)
  const setSession = useAuthStore((state) => state.setSession)
  const inviteFromUrl = readCohortInviteParams(location.search)
  const [mode, setMode] = useState<AuthMode>(inviteFromUrl ? 'register' : 'sign-in')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [sessionReady, setSessionReady] = useState(false)
  const [oauthProviders, setOauthProviders] = useState<OAuthProvider[]>([])

  const defaultRedirect = inviteFromUrl ? '/app/welcome' : '/app/home'
  const redirectTo = (location.state as { from?: string } | null)?.from ?? defaultRedirect

  useEffect(() => {
    void fetchOauthProviders()
      .then((response) => setOauthProviders(response.providers))
      .catch(() => setOauthProviders([]))
  }, [])

  if (accessToken || sessionReady) {
    return <CohortInviteRedirect to={redirectTo} search={location.search} />
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsSubmitting(true)
    setError(null)
    setInfo(null)
    try {
      if (mode === 'sign-in') {
        const session = await login({ email, password })
        setSession(session)
        setSessionReady(true)
        return
      }
      const result = await register({ email, password, displayName })
      if (isAuthSession(result)) {
        setSession(result)
        setSessionReady(true)
      } else {
        setInfo(result.message)
        setMode('sign-in')
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to authenticate')
    } finally {
      setIsSubmitting(false)
    }
  }

  const googleProvider = oauthProviders.find((provider) => provider.id === 'google')
  const googleAuthorizationUrl =
    googleProvider && isHttpOrHttpsUrl(googleProvider.authorizationUrl)
      ? googleProvider.authorizationUrl
      : null

  return (
    <main className="v2-auth-page">
      <section className="v2-auth-hero">
        <V2Brand to="/" className="v2-auth-brand" />
        <div className="auth-hero-copy">
          <h1>Where your<br />courses come<br /><span>together</span></h1>
          <p>Chat, questions, resources, and<br />office hours — one place per course.</p>
        </div>
        <div className="auth-course-preview" aria-hidden="true">
          <div className="preview-card preview-back two" />
          <div className="preview-card preview-back one" />
          <article className="preview-card preview-front">
            <div className="preview-title"><span>CS</span><strong>CS 101 — Intro to<br />Computer Science</strong></div>
            <div className="preview-tabs">
              <span><MessageSquare />Chat</span>
              <span><HelpCircle />Questions</span>
              <span><FileText />Resources</span>
              <span><CalendarDays />Office Hours</span>
            </div>
            <div className="preview-message"><i>AJ</i><p><strong>Dr. Alex Johnson</strong><small>Welcome to CS 101! I&apos;m excited to learn together…</small></p><time>2h ago</time></div>
          </article>
        </div>
      </section>

      <section className="v2-auth-panel">
        <div className="v2-auth-card">
          {inviteFromUrl ? (
            <div className="v2-auth-invite">
              <span className="invite-course-icon">CS</span>
              <p><small>You&apos;ve been invited to join</small><strong>CS 101 — Intro to Computer Science</strong><span>Spring cohort · Dr. Alex Johnson</span></p>
            </div>
          ) : (
            <div className="v2-auth-invite compact">
              <span className="invite-course-icon">C</span>
              <p><small>Welcome to Chanter</small><strong>Your courses, conversations, and support</strong><span>Sign in to continue learning.</span></p>
            </div>
          )}

          <div className="v2-auth-tabs" role="tablist" aria-label="Authentication mode">
            <button type="button" className={mode === 'sign-in' ? 'active' : undefined} onClick={() => setMode('sign-in')}>Sign in</button>
            <button type="button" className={mode === 'register' ? 'active' : undefined} onClick={() => setMode('register')}>Create account</button>
          </div>

          <form className="v2-auth-form" onSubmit={handleSubmit}>
            {mode === 'register' ? (
              <label>Full name<input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Sam Lee" required autoComplete="name" /></label>
            ) : null}
            <label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" required autoComplete="email" /></label>
            <label>Password<span className="password-field"><input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="••••••••••••••••" required minLength={8} autoComplete={mode === 'sign-in' ? 'current-password' : 'new-password'} /><button type="button" aria-label={showPassword ? 'Hide password' : 'Show password'} onClick={() => setShowPassword((current) => !current)}>{showPassword ? <EyeOff /> : <Eye />}</button></span></label>
            {mode === 'sign-in' ? (
              <p className="auth-forgot"><Link to="/forgot-password">Forgot password?</Link></p>
            ) : null}
            {error ? <p role="alert" className="v2-auth-error">{error}</p> : null}
            {info ? <p role="status" className="v2-auth-info">{info}</p> : null}
            <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Working…' : mode === 'register' ? `Create account${inviteFromUrl ? ' & join CS 101' : ''}` : 'Sign in'}</button>
          </form>

          <div className="auth-divider"><span />or<span /></div>
          {googleAuthorizationUrl ? (
            <a className="google-button" href={googleAuthorizationUrl}>
              <b aria-hidden="true">G</b> Continue with Google
            </a>
          ) : (
            <>
              <button type="button" className="google-button" disabled aria-describedby="google-sign-in-status">
                <b aria-hidden="true">G</b> Continue with Google
              </button>
              <p id="google-sign-in-status" className="auth-provider-status">
                Google sign-in is available when CHANTER_OAUTH_GOOGLE_CLIENT_ID / SECRET are set.
              </p>
            </>
          )}
          <p className="auth-terms">By continuing you agree to the <Link to="/terms">Terms</Link></p>
          <Link className="auth-back" to="/">Back to Chanter</Link>
        </div>
      </section>
    </main>
  )
}
