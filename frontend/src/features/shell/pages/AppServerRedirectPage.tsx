import type { ReactNode } from 'react'
import { Navigate, useParams } from 'react-router-dom'

import { useAccessibleStudyServersQuery } from '../hooks/use-shell-queries'

export function AppServerRedirectPage() {
  const { serverId } = useParams()
  const serversQuery = useAccessibleStudyServersQuery()

  if (!serverId) {
    if (serversQuery.isLoading) {
      return <ShellMessage>Loading study servers…</ShellMessage>
    }

    if (serversQuery.isError) {
      return <ShellMessage>Could not load study servers.</ShellMessage>
    }

    const firstServer = serversQuery.data?.[0]
    if (!firstServer) {
      return <Navigate to="/app/onboarding/create-study-server" replace />
    }

    return <Navigate to={`/app/servers/${firstServer.id}/home`} replace />
  }

  return <Navigate to={`/app/servers/${serverId}/home`} replace />
}

export function AppHomeRedirectPage() {
  return <AppServerRedirectPage />
}

function ShellMessage({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-1 items-center justify-center p-8">
      <p className="max-w-md text-center text-sm text-app-muted">{children}</p>
    </div>
  )
}
