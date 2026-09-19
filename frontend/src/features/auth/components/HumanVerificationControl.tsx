import { useEffect, useRef, useState } from 'react'
import { fetchVerificationOptions, type HumanVerification, type VerificationOptions } from '../auth-api'
import './human-verification.css'

type Turnstile = {
  render: (element: HTMLElement, options: {
    sitekey: string; action: string; size: 'flexible' | 'compact'; theme: 'light'
    callback: (token: string) => void
    'expired-callback': () => void
    'error-callback': () => void
  }) => string
  remove: (id: string) => void
}
declare global { interface Window { turnstile?: Turnstile } }

let scriptPromise: Promise<Turnstile> | undefined
function loadChallenge(): Promise<Turnstile> {
  if (window.turnstile) return Promise.resolve(window.turnstile)
  scriptPromise ??= new Promise<Turnstile>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
    script.async = true
    const timeout = window.setTimeout(() => finish(new Error('Verification could not load')), 8000)
    function finish(error?: Error) {
      window.clearTimeout(timeout)
      script.onload = null
      script.onerror = null
      if (error || !window.turnstile) {
        script.remove()
        reject(error ?? new Error('Verification is unavailable'))
      } else resolve(window.turnstile)
    }
    script.onload = () => finish()
    script.onerror = () => finish(new Error('Verification could not load'))
    document.head.append(script)
  }).catch((error: unknown) => { scriptPromise = undefined; throw error })
  return scriptPromise
}

export function HumanVerificationControl({ action, onChange }: {
  action: 'register' | 'recovery'
  onChange: (proof: HumanVerification | null) => void
}) {
  const [options, setOptions] = useState<VerificationOptions | null>(null)
  const [emailAlternative, setEmailAlternative] = useState(false)
  const [unavailable, setUnavailable] = useState(false)
  const container = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let mounted = true
    void fetchVerificationOptions().then((value) => {
      if (!mounted) return
      setOptions(value)
      if (!value.enabled) onChange({})
    }).catch(() => {
      if (mounted) { setOptions({ enabled: true, siteKey: null }); setUnavailable(true) }
    })
    return () => { mounted = false }
  }, [onChange])

  useEffect(() => {
    if (!options?.enabled || !options.siteKey || emailAlternative) return
    let mounted = true
    let widget: string | undefined
    let api: Turnstile | undefined
    let size: 'flexible' | 'compact' | undefined
    let generation = 0
    const renderWidget = () => {
      if (!mounted || !container.current || !api) return
      const nextSize = container.current.clientWidth < 300 ? 'compact' : 'flexible'
      if (size === nextSize) return
      if (widget !== undefined) { api.remove(widget); onChange(null) }
      size = nextSize
      const current = ++generation
      widget = api.render(container.current, {
        sitekey: options.siteKey!, action, size, theme: 'light',
        callback: (token) => { if (mounted && current === generation) onChange({ token }) },
        'expired-callback': () => { if (mounted && current === generation) onChange(null) },
        'error-callback': () => { if (mounted && current === generation) { onChange(null); setUnavailable(true) } },
      })
    }
    void loadChallenge().then((loaded) => {
      if (!mounted || !container.current) return
      api = loaded
      renderWidget()
      window.addEventListener('resize', renderWidget)
    }).catch(() => { if (mounted) setUnavailable(true) })
    return () => { mounted = false; window.removeEventListener('resize', renderWidget); if (widget !== undefined) api?.remove(widget) }
  }, [action, emailAlternative, onChange, options])

  if (options && !options.enabled) return null
  return <div className="auth-verification">
    {emailAlternative ? <p className="auth-lede" role="status">Continue with a link sent to your email.</p> : <>
      <div ref={container} className="auth-verification-widget" />
      <p className="auth-lede" aria-live="polite">{unavailable ? 'Verification could not load. You can continue by email.'
        : options ? 'Complete the check, or continue by email.' : 'Checking verification options…'}</p>
      <button className="auth-verification-alternative" type="button" onClick={() => {
        setEmailAlternative(true)
        onChange({ method: 'email' })
      }}>Use email verification instead</button>
    </>}
  </div>
}
