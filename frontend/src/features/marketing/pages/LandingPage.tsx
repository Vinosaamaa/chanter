import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { Button } from '../../../components/ui/button'
import { Card, CardDescription, CardTitle } from '../../../components/ui/card'
import { fetchGatewayHealth } from '../../../lib/api-client'

export function LandingPage() {
  const navigate = useNavigate()
  const healthQuery = useQuery({
    queryKey: ['gateway-health'],
    queryFn: fetchGatewayHealth,
    retry: false,
  })

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-10 px-6 py-16">
      <header className="flex flex-col gap-4">
        <p className="text-sm font-medium uppercase tracking-[0.2em] text-app-accent">Chanter</p>
        <h1 className="max-w-3xl text-4xl font-semibold tracking-tight text-app-text md:text-5xl">
          Discord for learning communities, with AI teaching assistants built in.
        </h1>
        <p className="max-w-2xl text-lg text-app-muted">
          Study Servers, course channels, instructor operations, and grounded AI support — in one
          browser product shell.
        </p>
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => navigate('/sign-in')}>Sign in</Button>
          <Button variant="secondary" onClick={() => navigate('/app')}>
            Open app shell
          </Button>
        </div>
      </header>

      <Card>
        <CardTitle>Gateway health</CardTitle>
        <CardDescription>
          TanStack Query + shared API client wired to the Vite proxy (`/actuator/health`).
        </CardDescription>
        <p className="mt-4 text-sm text-app-text">
          {healthQuery.isLoading && 'Checking gateway…'}
          {healthQuery.isError && 'Gateway unreachable — start backend services for a live check.'}
          {healthQuery.isSuccess && `Status: ${healthQuery.data.status}`}
        </p>
        <p className="mt-4 text-sm text-app-muted">
          Legacy API demo:{' '}
          <Link className="text-app-accent hover:text-app-accent-hover" to="/dev/demo">
            /dev/demo
          </Link>
        </p>
      </Card>
    </div>
  )
}
