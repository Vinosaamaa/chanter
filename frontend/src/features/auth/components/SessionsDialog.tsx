import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Monitor, ShieldCheck, Smartphone, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

import { useAuthStore } from '../../../stores/auth-store'
import { fetchSessions, type BrowserSession } from '../auth-api'
import { revokeBrowserSession } from '../browser-session'
import { useSignOut } from '../hooks/use-sign-out'
import './sessions.css'

function deviceName(userAgent: string | null): string {
  const agent = userAgent ?? ''
  const browser = /Edg\//.test(agent) ? 'Edge' : /Firefox\//.test(agent) ? 'Firefox'
    : /Chrome\//.test(agent) ? 'Chrome' : /Safari\//.test(agent) ? 'Safari' : 'Browser'
  const device = /iPhone/.test(agent) ? 'iPhone' : /iPad/.test(agent) ? 'iPad'
    : /Android/.test(agent) ? 'Android' : /Windows/.test(agent) ? 'Windows'
      : /Macintosh|Mac OS/.test(agent) ? 'Mac' : /Linux/.test(agent) ? 'Linux' : null
  return device ? `${browser} on ${device}` : browser
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export function SessionsDialog({ onClose }: { onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const userId = useAuthStore((state) => state.user?.id)
  const queryClient = useQueryClient()
  const signOut = useSignOut()
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [signingOut, setSigningOut] = useState(false)
  const queryKey = ['auth-sessions', userId]
  const sessionsQuery = useQuery({ queryKey, queryFn: fetchSessions, enabled: Boolean(userId) })
  const revoke = useMutation({
    mutationFn: revokeBrowserSession,
    onSuccess: (_result, id) => {
      const removed = sessionsQuery.data?.sessions.find((session) => session.id === id)
      queryClient.setQueryData<{ sessions: BrowserSession[] }>(queryKey, (data) => data && ({
        sessions: data.sessions.filter((session) => session.id !== id),
      }))
      setMessage(`${deviceName(removed?.userAgent ?? null)} signed out.`)
      closeButtonRef.current?.focus()
    },
    onError: () => {
      setError('Could not sign out this device. Check your connection and try again.')
      closeButtonRef.current?.focus()
    },
  })

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])

  const endSession = (session: BrowserSession) => {
    setError('')
    setMessage('')
    if (session.current) {
      setSigningOut(true)
      void signOut()
    } else {
      revoke.mutate(session.id)
    }
  }

  const closeDialog = () => {
    dialogRef.current?.close()
    onClose()
  }

  return (
    <dialog ref={dialogRef} className="settings-modal sessions-dialog" aria-labelledby="session-settings-title"
      aria-describedby="session-settings-description" onCancel={(event) => {
        event.preventDefault()
        closeDialog()
      }} onKeyDown={(event) => {
        if (event.key !== 'Tab') return
        const controls = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'))
        const first = controls[0]
        const last = controls.at(-1)
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last?.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first?.focus()
        }
      }}>
      <aside>
        <h2>Settings</h2>
        <small>USER ACCOUNT</small>
        <p className="session-settings-nav"><ShieldCheck size={19} />Sessions and devices</p>
      </aside>
      <main>
        <button ref={closeButtonRef} type="button" className="settings-close" aria-label="Close session settings" onClick={closeDialog}><X size={22} /></button>
        <header>
          <h1 id="session-settings-title">Sessions and devices</h1>
          <p id="session-settings-description">See where you are signed in. Sign out any device you no longer use or recognize.</p>
        </header>
        {sessionsQuery.isPending ? <p role="status">Loading your sessions…</p> : null}
        {sessionsQuery.isError ? <div role="alert" className="session-error">
          <p>Could not load your sessions.</p>
          <button type="button" className="v2-primary-button" onClick={() => void sessionsQuery.refetch()}>Try again</button>
        </div> : null}
        {error ? <p role="alert" className="session-error">{error}</p> : null}
        {message ? <p role="status" className="session-notice">{message}</p> : null}
        {sessionsQuery.data ? <>
          <ul className="session-device-list" aria-label="Active sessions">
            {sessionsQuery.data.sessions.map((session) => {
              const name = deviceName(session.userAgent)
              return <li key={session.id} aria-label={name}>
                <span className="session-device-icon" aria-hidden="true">
                  {/iPhone|iPad|Android/.test(session.userAgent ?? '') ? <Smartphone size={22} /> : <Monitor size={22} />}
                </span>
                <div className="session-device-details">
                  <h2>{name}{session.current ? <span className="session-current">This device</span> : null}</h2>
                  <p>Last active <time dateTime={session.lastUsedAt}>{formatDate(session.lastUsedAt)}</time></p>
                  <p>Signed in <time dateTime={session.createdAt}>{formatDate(session.createdAt)}</time></p>
                </div>
                <button type="button" className="session-revoke" disabled={revoke.isPending || signingOut}
                  aria-label={session.current ? 'Sign out this device' : `Sign out ${name}`}
                  onClick={() => endSession(session)}>
                  {(revoke.isPending && revoke.variables === session.id) || (signingOut && session.current) ? 'Signing out…' : 'Sign out'}
                </button>
              </li>
            })}
          </ul>
          {sessionsQuery.data.sessions.length === 0 ? <p>No active sessions were found.</p> : null}
          <p className="session-help">Other devices can keep access for up to 15 minutes, then must sign in again.</p>
        </> : null}
      </main>
    </dialog>
  )
}
