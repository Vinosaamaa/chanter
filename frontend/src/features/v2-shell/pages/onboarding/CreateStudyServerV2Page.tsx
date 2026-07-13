import { useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Building2, Check, Code2, UsersRound, X } from 'lucide-react'

import { createStudyServer } from '../../../onboarding/onboarding-api'
import { formatUserFacingApiError, isUnauthorizedApiError } from '../../../../lib/format-api-error'
import { useAuthStore } from '../../../../stores/auth-store'
import { HomePage } from '../HomePage'

type ServerType = 'school' | 'program' | 'personal'

const serverTypes = [
  {
    id: 'school' as const,
    icon: Building2,
    title: 'School or university',
    description: 'For classes, departments, and academic groups',
  },
  {
    id: 'program' as const,
    icon: Code2,
    title: 'Program or bootcamp',
    description: 'For cohorts, bootcamps, and training programs',
  },
  {
    id: 'personal' as const,
    icon: UsersRound,
    title: 'Personal small group',
    description: 'For friends, study groups, and small communities',
  },
]

const stepNames = ['Server type', 'Basics', 'Invite team', 'Review']

export function CreateStudyServerV2Page() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const submittingRef = useRef(false)
  const [step, setStep] = useState(0)
  const [serverType, setServerType] = useState<ServerType>('school')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [invitees, setInvitees] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const close = () => navigate('/app/home')

  const continueWizard = () => {
    if (step === 1 && !name.trim()) {
      setError('Enter a Study Server name to continue.')
      return
    }
    setError(null)
    setStep((current) => Math.min(current + 1, 3))
  }

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (step < 3) {
      continueWizard()
      return
    }
    if (!name.trim() || submittingRef.current) return

    submittingRef.current = true
    setIsSubmitting(true)
    setError(null)
    try {
      const created = await createStudyServer(name.trim())
      if (description.trim()) {
        sessionStorage.setItem(`chanter:study-server-description:${created.id}`, description.trim())
      }
      await queryClient.invalidateQueries({ queryKey: ['study-servers'] })
      navigate(`/app/servers/${created.id}/community/announcements`, { replace: true })
    } catch (caught) {
      if (isUnauthorizedApiError(caught)) {
        clearSession()
        navigate('/sign-in', { replace: true, state: { from: '/app/onboarding/create-study-server' } })
        return
      }
      setError(formatUserFacingApiError(caught, 'Unable to create Study Server.'))
    } finally {
      submittingRef.current = false
      setIsSubmitting(false)
    }
  }

  return (
    <div className="v2-overlay-page">
      <HomePage />
      <div className="v2-modal-backdrop">
        <form className="study-server-modal" onSubmit={onSubmit} aria-labelledby="create-server-title">
          <header>
            <div>
              <h1 id="create-server-title">Create Study Server</h1>
              <p>Step {step + 1} of 4 · {stepNames[step]}</p>
            </div>
            <button type="button" className="v2-icon-button" aria-label="Close" onClick={close}>
              <X size={23} />
            </button>
          </header>

          <div className="wizard-progress" aria-label={`Step ${step + 1} of 4`}>
            {stepNames.map((label, index) => (
              <span key={label} className={index <= step ? 'active' : undefined} />
            ))}
          </div>

          <div className="study-server-modal-body">
            {step === 0 ? (
              <>
                <h2>Server type</h2>
                <p className="wizard-hint">What best describes your study community?</p>
                <div className="server-type-grid">
                  {serverTypes.map(({ id, icon: Icon, title, description: copy }) => (
                    <button
                      key={id}
                      type="button"
                      className={serverType === id ? 'selected' : undefined}
                      aria-pressed={serverType === id}
                      onClick={() => setServerType(id)}
                    >
                      <i className="radio-dot" />
                      <span className={`server-type-icon ${id}`}><Icon size={30} /></span>
                      <strong>{title}</strong>
                      <small>{copy}</small>
                    </button>
                  ))}
                </div>
              </>
            ) : null}

            {step === 1 ? (
              <div className="wizard-fields">
                <h2>Tell us about your Study Server</h2>
                <p className="wizard-hint">You can update these details later in Settings.</p>
                <label>
                  Study Server name
                  <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Spring Bootcamp Hub" autoFocus />
                </label>
                <label>
                  Description
                  <textarea value={description} onChange={(event) => setDescription(event.target.value.slice(0, 200))} placeholder="A focused place for your learning community." rows={4} />
                  <small>{description.length}/200</small>
                </label>
              </div>
            ) : null}

            {step === 2 ? (
              <div className="wizard-fields">
                <h2>Invite your team</h2>
                <p className="wizard-hint">Add instructors or administrators now, or skip this step.</p>
                <label>
                  Email addresses
                  <textarea value={invitees} onChange={(event) => setInvitees(event.target.value)} placeholder="alex@school.edu, priya@school.edu" rows={5} />
                </label>
                <p className="wizard-note">Invites are prepared after your Study Server is created.</p>
              </div>
            ) : null}

            {step === 3 ? (
              <div className="wizard-review">
                <span className="review-icon"><Check size={26} /></span>
                <h2>{name}</h2>
                <p>{description || 'A new home for your learning community.'}</p>
                <dl>
                  <div><dt>Type</dt><dd>{serverTypes.find((item) => item.id === serverType)?.title}</dd></div>
                  <div><dt>Team invites</dt><dd>{invitees.trim() ? invitees.split(',').length : 'None yet'}</dd></div>
                  <div><dt>Default spaces</dt><dd>Announcements, lounge, and study room</dd></div>
                </dl>
              </div>
            ) : null}

            {error ? <p role="alert" className="v2-form-error">{error}</p> : null}
          </div>

          <footer>
            <button type="button" className="v2-secondary-button" onClick={step === 0 ? close : () => setStep((current) => current - 1)} disabled={isSubmitting}>
              {step === 0 ? 'Cancel' : 'Back'}
            </button>
            <button type="submit" className="v2-primary-button" disabled={isSubmitting}>
              {isSubmitting ? 'Creating…' : step === 3 ? 'Create Study Server' : 'Continue'}
            </button>
          </footer>
        </form>
      </div>
    </div>
  )
}
