import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'

import { Button } from '../../../components/ui/button'
import { Card, CardDescription, CardTitle } from '../../../components/ui/card'
import { login, register } from '../auth-api'
import { useAuthStore } from '../../../stores/auth-store'

type AuthMode = 'sign-in' | 'register'

export function SignInPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const accessToken = useAuthStore((state) => state.accessToken)
  const setSession = useAuthStore((state) => state.setSession)
  const [mode, setMode] = useState<AuthMode>('sign-in')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/app'

  if (accessToken) {
    return <Navigate to={redirectTo} replace />
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsSubmitting(true)
    setError(null)

    try {
      const session =
        mode === 'sign-in'
          ? await login({ email, password })
          : await register({ email, password, displayName })
      setSession(session)
      navigate(redirectTo, { replace: true })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to authenticate')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-12">
      <Card className="w-full max-w-md">
        <CardTitle>{mode === 'sign-in' ? 'Sign in' : 'Create account'}</CardTitle>
        <CardDescription>
          Email and password for the production MVP. SSO and onboarding polish ship later.
        </CardDescription>

        <div className="mt-4 flex gap-2">
          <Button
            type="button"
            variant={mode === 'sign-in' ? 'primary' : 'secondary'}
            onClick={() => setMode('sign-in')}
          >
            Sign in
          </Button>
          <Button
            type="button"
            variant={mode === 'register' ? 'primary' : 'secondary'}
            onClick={() => setMode('register')}
          >
            Register
          </Button>
        </div>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit}>
          {mode === 'register' ? (
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-app-muted">Display name</span>
              <input
                className="rounded-md border border-app-border bg-app-bg px-3 py-2 text-app-text"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                required
                autoComplete="name"
              />
            </label>
          ) : null}

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-app-muted">Email</span>
            <input
              className="rounded-md border border-app-border bg-app-bg px-3 py-2 text-app-text"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              autoComplete="email"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-app-muted">Password</span>
            <input
              className="rounded-md border border-app-border bg-app-bg px-3 py-2 text-app-text"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              minLength={8}
              autoComplete={mode === 'sign-in' ? 'current-password' : 'new-password'}
            />
          </label>

          {error ? <p className="text-sm text-red-400">{error}</p> : null}

          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Working…' : mode === 'sign-in' ? 'Sign in' : 'Create account'}
          </Button>
        </form>

        <Link className="mt-4 block text-center text-sm text-app-muted hover:text-app-text" to="/">
          Back to landing
        </Link>
      </Card>
    </div>
  )
}
