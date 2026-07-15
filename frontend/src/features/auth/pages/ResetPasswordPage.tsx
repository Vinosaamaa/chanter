import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { resetPassword } from '../auth-api'
import { V2Brand } from '../../v2-shell/components/V2Brand'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsSubmitting(true)
    setError(null)
    setMessage(null)
    try {
      const result = await resetPassword(token, password)
      setMessage(result.message)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to reset password')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="v2-auth-page compact-auth">
      <section className="v2-auth-panel solo">
        <div className="v2-auth-card">
          <V2Brand to="/" className="v2-auth-brand" />
          <h1>Reset password</h1>
          {!token ? (
            <p role="alert" className="v2-auth-error">Reset token missing. Use the link from your email.</p>
          ) : (
            <form className="v2-auth-form" onSubmit={handleSubmit}>
              <label>New password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required minLength={8} autoComplete="new-password" /></label>
              {error ? <p role="alert" className="v2-auth-error">{error}</p> : null}
              {message ? <p role="status" className="v2-auth-info">{message}</p> : null}
              <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Updating…' : 'Update password'}</button>
            </form>
          )}
          <Link className="auth-back" to="/sign-in">Back to sign in</Link>
        </div>
      </section>
    </main>
  )
}
