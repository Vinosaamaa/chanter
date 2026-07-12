import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'

import { Button } from '../../../components/ui/button'
import { completePendingCohortJoin } from '../../onboarding/cohort-invite'
import { joinCohort } from '../../onboarding/onboarding-api'

type CohortInviteRedirectProps = {
  to: string
}

type InviteRedirectStatus = 'joining' | 'failed' | 'ready'

export function CohortInviteRedirect({ to }: CohortInviteRedirectProps) {
  const [status, setStatus] = useState<InviteRedirectStatus>('joining')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    void completePendingCohortJoin(joinCohort).then((result) => {
      if (cancelled) {
        return
      }
      setStatus(result === 'failed' ? 'failed' : 'ready')
    })
    return () => {
      cancelled = true
    }
  }, [retryKey])

  const onRetry = () => {
    setStatus('joining')
    setRetryKey((value) => value + 1)
  }

  if (status === 'joining') {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-app-muted">
        Joining cohort…
      </div>
    )
  }

  if (status === 'failed') {
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
