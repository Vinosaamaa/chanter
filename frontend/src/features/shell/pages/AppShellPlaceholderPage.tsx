import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { Card, CardDescription, CardTitle } from '../../../components/ui/card'
import { fetchGatewayHealth } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'

export function AppShellPlaceholderPage() {
  const user = useAuthStore((state) => state.user)

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
          proves routing, layout primitives, auth session, and API wiring.
        </CardDescription>
        <p className="mt-4 text-sm text-app-muted">
          User id: {user?.id ?? 'unknown'}
        </p>
        <p className="mt-2 text-sm text-app-muted">
          Gateway:{' '}
          {healthQuery.isLoading
            ? 'checking…'
            : healthQuery.isSuccess
              ? healthQuery.data.status
              : 'unavailable'}
        </p>
        <div className="mt-4">
          <Link className="text-sm text-app-accent hover:text-app-accent-hover" to="/">
            Back to landing
          </Link>
        </div>
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
