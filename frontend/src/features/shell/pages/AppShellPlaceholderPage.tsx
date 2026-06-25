import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { Button } from '../../../components/ui/button'
import { Card, CardDescription, CardTitle } from '../../../components/ui/card'
import { fetchGatewayHealth } from '../../../lib/api-client'
import { useAppStore } from '../../../stores/app-store'

export function AppShellPlaceholderPage() {
  const navigate = useNavigate()
  const sessionUserId = useAppStore((state) => state.sessionUserId)
  const setSessionUserId = useAppStore((state) => state.setSessionUserId)

  const healthQuery = useQuery({
    queryKey: ['gateway-health', 'app-shell'],
    queryFn: fetchGatewayHealth,
    retry: false,
  })

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <Card>
        <CardTitle>Authenticated shell placeholder</CardTitle>
        <CardDescription>
          Course channels, realtime chat, and mockup-aligned screens land in #50–#59. This route
          proves routing, layout primitives, Zustand, and API wiring.
        </CardDescription>
        <div className="mt-4 flex flex-wrap gap-3">
          <Button
            variant="secondary"
            onClick={() => setSessionUserId(sessionUserId ? null : '00000000-0000-0000-0000-000000000001')}
          >
            {sessionUserId ? 'Clear session placeholder' : 'Set session placeholder'}
          </Button>
          <Button variant="ghost" onClick={() => navigate('/')}>
            Back to landing
          </Button>
        </div>
        <p className="mt-4 text-sm text-app-muted">
          Session user id: {sessionUserId ?? 'none (auth ships in #49 + #30)'}
        </p>
        <p className="mt-2 text-sm text-app-muted">
          Gateway:{' '}
          {healthQuery.isLoading
            ? 'checking…'
            : healthQuery.isSuccess
              ? healthQuery.data.status
              : 'unavailable'}
        </p>
      </Card>
      <p className="text-sm text-app-muted">
        Need the legacy vertical-slice harness?{' '}
        <Link className="text-app-accent hover:text-app-accent-hover" to="/dev/demo">
          Open /dev/demo
        </Link>
      </p>
    </div>
  )
}
