import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'

import { completePendingCohortJoin } from '../../onboarding/cohort-invite'
import { joinCohort } from '../../onboarding/onboarding-api'

type CohortInviteRedirectProps = {
  to: string
}

export function CohortInviteRedirect({ to }: CohortInviteRedirectProps) {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    void completePendingCohortJoin(joinCohort).finally(() => setReady(true))
  }, [])

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-app-muted">
        Joining cohort…
      </div>
    )
  }

  return <Navigate to={to} replace />
}
