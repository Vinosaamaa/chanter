import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'

import { Button } from '../../../components/ui/button'
import { completePendingCohortJoin } from '../../onboarding/cohort-invite'
import { joinCohort } from '../../onboarding/onboarding-api'

type CohortInviteRedirectProps = {
  to: string
}

export function CohortInviteRedirect({ to }: CohortInviteRedirectProps) {
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    void completePendingCohortJoin(joinCohort).then((result) => {
      if (cancelled) {
        return
      }
      setFailed(result === 'failed')
      setReady(true)
    })
    return () => {
      cancelled = true
    }
  }, [attempt])

  const onRetry = () => {
    setReady(false)
    setFailed(false)
    setAttempt((value) => value + 1)
  }

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-app-muted">
        Joining cohort…
      </div>
    )
  }

  if (failed) {
    return (
      <div className="flex min-h-screen items-center justify-center px-6">
        <div className="w-full max-w-md rounded-xl border border-app-border bg-app-surface p-6 text-center">
          <h1 className="text-lg font-semibold text-app-text">Could not join cohort</h1>
          <p className="mt-2 text-sm text-app-muted">
            Check that your invite link is complete and try again, or continue to the app.
          </p>
          <div className="mt-4 flex justify-center gap-2">
            <Button type="button" variant="secondary" onClick={onRetry}>
              Retry join
            </Button>
            <Link
              to={to}
              className="inline-flex items-center rounded-lg bg-app-accent px-4 py-2 text-sm font-medium text-white hover:bg-app-accent-hover"
            >
              Continue to app
            </Link>
          </div>
        </div>
      </div>
    )
  }

  return <Navigate to={to} replace />
}
