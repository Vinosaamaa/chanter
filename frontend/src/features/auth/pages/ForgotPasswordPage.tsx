import { useState } from 'react'
import { Link } from 'react-router-dom'

import { forgotPassword } from '../auth-api'
import { V2Brand } from '../../v2-shell/components/V2Brand'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsSubmitting(true)
    setError(null)
    setMessage(null)
    try {
      const result = await forgotPassword(email)
      setMessage(result.message)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to request password reset')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="v2-auth-page compact-auth">
      <section className="v2-auth-panel solo">
        <div className="v2-auth-card">
          <V2Brand to="/" className="v2-auth-brand" />
          <h1>Forgot password</h1>
          <p className="auth-lede">Enter your email and we will send a reset link when the account exists.</p>
          <form className="v2-auth-form" onSubmit={handleSubmit}>
            <label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required autoComplete="email" /></label>
            {error ? <p role="alert" className="v2-auth-error">{error}</p> : null}
            {message ? <p role="status" className="v2-auth-info">{message}</p> : null}
            <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Sending…' : 'Send reset link'}</button>
          </form>
          <Link className="auth-back" to="/sign-in">Back to sign in</Link>
        </div>
      </section>
    </main>
  )
}
