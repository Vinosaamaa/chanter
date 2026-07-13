import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Link2, Plus } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

import { formatUserFacingApiError } from '../../../../lib/format-api-error'
import { readCohortInviteInput } from '../../../onboarding/cohort-invite'
import { joinCohort } from '../../../onboarding/onboarding-api'

export function JoinOrCreatePage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [inviteLink, setInviteLink] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [joining, setJoining] = useState(false)

  const submitJoin = async (event: FormEvent) => {
    event.preventDefault()
    const invite = readCohortInviteInput(inviteLink)
    if (!invite) {
      setError('Enter the complete Cohort invite link.')
      return
    }

    setJoining(true)
    setError(null)
    try {
      await joinCohort(invite.cohortId, invite.inviteCode)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['study-servers'] }),
        queryClient.invalidateQueries({ queryKey: ['study-server-navigation'] }),
      ])
      navigate('/app/home', { replace: true })
    } catch (caught) {
      setError(formatUserFacingApiError(caught, 'Unable to join this Cohort.'))
    } finally {
      setJoining(false)
    }
  }

  return (
    <section className="v2-workspace-page join-or-create-page">
      <header>
        <h1>Join or create</h1>
        <p>Enter a Cohort invite or start a new learning community.</p>
      </header>

      <div className="join-or-create-grid">
        <form onSubmit={(event) => void submitJoin(event)}>
          <span className="join-choice-icon"><Link2 /></span>
          <h2>Join a Cohort</h2>
          <label>
            Cohort invite link
            <input
              value={inviteLink}
              onChange={(event) => setInviteLink(event.target.value)}
              placeholder="Paste your invite link"
              autoComplete="off"
              required
            />
          </label>
          {error ? <p role="alert" className="modal-error">{error}</p> : null}
          <button type="submit" className="v2-primary-button" disabled={joining}>
            {joining ? 'Joining…' : 'Join cohort'}<ArrowRight />
          </button>
        </form>

        <article>
          <span className="join-choice-icon create"><Plus /></span>
          <h2>Create a Study Server</h2>
          <p>Set up a hub with its first Course and Cohort.</p>
          <Link className="v2-outline-button" to="/app/onboarding/create-study-server">
            Create a Study Server<ArrowRight />
          </Link>
        </article>
      </div>
    </section>
  )
}
