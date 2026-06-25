import type { ReactNode } from 'react'
import { Navigate, useParams } from 'react-router-dom'

import { useAccessibleStudyServersQuery, useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import { defaultChannelPath } from '../shell-routes'

export function AppServerRedirectPage() {
  const { serverId } = useParams()
  const serversQuery = useAccessibleStudyServersQuery()
  const navigationQuery = useStudyServerNavigationQuery(serverId)

  if (!serverId) {
    if (serversQuery.isLoading) {
      return <ShellMessage>Loading study servers…</ShellMessage>
    }

    if (serversQuery.isError) {
      return <ShellMessage>Could not load study servers.</ShellMessage>
    }

    const firstServer = serversQuery.data?.[0]
    if (!firstServer) {
      return (
        <ShellMessage>
          No study servers yet. Use the API demo harness or onboarding (#56) to create one.
        </ShellMessage>
      )
    }

    return <Navigate to={`/app/servers/${firstServer.id}`} replace />
  }

  if (navigationQuery.isLoading) {
    return <ShellMessage>Loading navigation…</ShellMessage>
  }

  if (navigationQuery.isError) {
    return <ShellMessage>You do not have access to this study server.</ShellMessage>
  }

  if (!navigationQuery.data) {
    return <ShellMessage>Study server not found.</ShellMessage>
  }

  const target = defaultChannelPath(serverId, navigationQuery.data)
  if (!target) {
    return (
      <ShellMessage>
        This study server has no channels yet. Create a course to unlock course channels.
      </ShellMessage>
    )
  }

  return <Navigate to={target} replace />
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
