import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { Button } from '../../../components/ui/button'
import { cn } from '../../../lib/cn'
import { formatUserFacingApiError, isUnauthorizedApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'
import { StudyServerIcon } from '../../shell/components/StudyServerIcon'

import { createStudyServer } from '../onboarding-api'

const steps = [
  { id: 'basics', title: 'Basic information', hint: 'Name your server and add a description.' },
  { id: 'invite', title: 'Invite people', hint: 'Plan who to invite after creation.' },
  { id: 'review', title: 'Review', hint: 'Confirm your settings and create.' },
] as const

type WizardStep = (typeof steps)[number]['id']

const descriptionLimit = 200

export function CreateStudyServerWizard() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const [step, setStep] = useState<WizardStep>('basics')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [inviteNote, setInviteNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const stepIndex = steps.findIndex((item) => item.id === step)
  const trimmedName = name.trim()
  const trimmedDescription = description.trim()

  const previewChannels = useMemo(
    () => [
      { group: 'Text channels', items: ['announcements', 'general'] },
      { group: 'Voice channels', items: ['study-room'] },
    ],
    [],
  )

  const goNext = () => {
    if (step === 'basics') {
      if (!trimmedName) {
        setError('Enter a Study Server name to continue.')
        return
      }
      setError(null)
      setStep('invite')
      return
    }
    if (step === 'invite') {
      setError(null)
      setStep('review')
    }
  }

  const goBack = () => {
    setError(null)
    if (step === 'invite') {
      setStep('basics')
    } else if (step === 'review') {
      setStep('invite')
    }
  }

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSubmitting) {
      return
    }
    if (step !== 'review' || !trimmedName) {
      return
    }

    setIsSubmitting(true)
    setError(null)

    try {
      const created = await createStudyServer(trimmedName)
      if (trimmedDescription) {
        try {
          sessionStorage.setItem(
            `chanter:study-server-description:${created.id}`,
            trimmedDescription,
          )
        } catch {
          // Description preview is optional when storage is unavailable.
        }
      }
      await queryClient.invalidateQueries({ queryKey: ['study-servers'] })
      navigate(`/app/servers/${created.id}/home`, { replace: true })
    } catch (caught) {
      if (isUnauthorizedApiError(caught)) {
        clearSession()
        navigate('/sign-in', {
          replace: true,
          state: { from: '/app/onboarding/create-study-server' },
        })
        return
      }
      setError(formatUserFacingApiError(caught, 'Unable to create Study Server.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-app-bg text-app-text">
      <div className="mx-auto grid min-h-screen max-w-6xl lg:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="border-b border-app-border px-6 py-8 lg:border-b-0 lg:border-r">
          <p className="text-lg font-semibold">Chanter</p>
          <h1 className="mt-6 text-xl font-semibold">Let&apos;s set up your Study Server</h1>
          <p className="mt-2 text-sm text-app-muted">
            Create a home for your community to learn, collaborate, and grow together.
          </p>

          <ol className="mt-8 space-y-4">
            {steps.map((item, index) => {
              const isActive = item.id === step
              const isComplete = index < stepIndex
              return (
                <li
                  key={item.id}
                  className={cn(
                    'rounded-lg border px-3 py-3',
                    isActive ? 'border-app-accent bg-app-accent/10' : 'border-app-border bg-app-surface',
                  )}
                >
                  <p className="text-sm font-medium text-app-text">
                    {index + 1}. {item.title}
                  </p>
                  <p className="mt-1 text-xs text-app-muted">{item.hint}</p>
                  {isComplete ? (
                    <p className="mt-2 text-[11px] font-medium text-emerald-300">Complete</p>
                  ) : null}
                </li>
              )
            })}
          </ol>

          <p className="mt-8 rounded-lg border border-app-border bg-app-surface p-3 text-xs text-app-muted">
            Study Servers are great for classes, bootcamps, clubs, and learning communities.
          </p>
        </aside>

        <main className="px-6 py-8">
          <form className="mx-auto max-w-3xl" onSubmit={onSubmit}>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
              Step {stepIndex + 1} of {steps.length}
            </p>
            <h2 className="mt-2 text-2xl font-semibold">{steps[stepIndex]?.title}</h2>

            {step === 'basics' ? (
              <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_240px]">
                <div className="space-y-4">
                  <label className="flex flex-col gap-1 text-sm">
                    <span className="text-app-muted">Study Server name *</span>
                    <input
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                      required
                      disabled={isSubmitting}
                      placeholder="e.g. Bootcamp Hub"
                      className="rounded-lg border border-app-border bg-app-surface px-3 py-2"
                    />
                  </label>

                  <label className="flex flex-col gap-1 text-sm">
                    <span className="text-app-muted">Description</span>
                    <textarea
                      value={description}
                      onChange={(event) => setDescription(event.target.value.slice(0, descriptionLimit))}
                      disabled={isSubmitting}
                      rows={4}
                      placeholder="A collaborative space for your cohort to learn together."
                      className="rounded-lg border border-app-border bg-app-surface px-3 py-2"
                    />
                    <span className="self-end text-xs text-app-muted">
                      {trimmedDescription.length}/{descriptionLimit}
                    </span>
                  </label>
                </div>

                <div className="rounded-xl border border-app-border bg-app-surface p-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">Preview</p>
                  <div className="mt-3 flex items-center gap-2">
                    <StudyServerIcon serverId={`preview:${trimmedName || 'study-server'}`} size="md" />
                    <span className="font-medium">{trimmedName || 'Study Server'}</span>
                  </div>
                  <div className="mt-4 space-y-3 text-xs text-app-muted">
                    {previewChannels.map((group) => (
                      <div key={group.group}>
                        <p className="font-semibold uppercase tracking-[0.12em]">{group.group}</p>
                        <ul className="mt-1 space-y-1">
                          {group.items.map((channel) => (
                            <li key={channel} className="text-app-text">
                              # {channel}
                            </li>
                          ))}
                        </ul>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : null}

            {step === 'invite' ? (
              <div className="mt-6 space-y-4">
                <p className="text-sm text-app-muted">
                  After creation you can copy an enrollment invite link from each course cohort page.
                  Optionally note classmates or emails you plan to invite.
                </p>
                <label className="flex flex-col gap-1 text-sm">
                  <span className="text-app-muted">Invite notes (optional)</span>
                  <textarea
                    value={inviteNote}
                    onChange={(event) => setInviteNote(event.target.value)}
                    disabled={isSubmitting}
                    rows={5}
                    placeholder="e.g. Share the invite link in #general after the first cohort is created."
                    className="rounded-lg border border-app-border bg-app-surface px-3 py-2"
                  />
                </label>
              </div>
            ) : null}

            {step === 'review' ? (
              <div className="mt-6 space-y-4 rounded-xl border border-app-border bg-app-surface p-5">
                <div className="flex items-center gap-3">
                  <StudyServerIcon serverId={`preview:${trimmedName}`} size="md" />
                  <div>
                    <p className="text-lg font-semibold">{trimmedName}</p>
                    <p className="text-sm text-app-muted">
                      {trimmedDescription || 'No description provided.'}
                    </p>
                  </div>
                </div>
                {inviteNote.trim() ? (
                  <p className="text-sm text-app-muted">
                    <span className="font-medium text-app-text">Invite plan:</span> {inviteNote.trim()}
                  </p>
                ) : null}
                <p className="text-xs text-app-muted">
                  Default channels (#announcements, #general, voice study room) will be created automatically.
                </p>
              </div>
            ) : null}

            {error ? (
              <p role="alert" className="mt-4 text-sm text-red-300">
                {error}
              </p>
            ) : null}

            <div className="mt-8 flex flex-wrap justify-between gap-3">
              <Button type="button" variant="secondary" disabled={step === 'basics' || isSubmitting} onClick={goBack}>
                Back
              </Button>
              {step === 'review' ? (
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Creating…' : 'Create Study Server'}
                </Button>
              ) : (
                <Button type="button" onClick={goNext}>
                  Continue
                </Button>
              )}
            </div>
          </form>
        </main>
      </div>
    </div>
  )
}
