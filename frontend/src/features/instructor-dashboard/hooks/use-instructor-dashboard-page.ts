import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { useAccessibleStudyServersQuery } from '../../shell/hooks/use-shell-queries'

import {
  fetchInstructorDashboard,
  fetchStudyServerDetails,
} from '../instructor-dashboard-api'
import type { InstructorDashboard } from '../instructor-dashboard-types'

type UseInstructorDashboardPageResult = {
  servers: Array<{ id: string; name: string }>
  selectedServerId: string | null
  setSelectedServerId: (serverId: string) => void
  dashboard: InstructorDashboard | null
  isOwner: boolean
  isLoading: boolean
  accessDenied: boolean
  error: string | null
  refresh: () => Promise<void>
}

function accessErrorMessage(caught: unknown): string {
  if (caught instanceof ApiError) {
    if (caught.status === 403) {
      return 'Only Study Server owners and course instructors can open the Instructor Dashboard.'
    }
    if (caught.body && caught.body.trim().length > 0) {
      try {
        const parsed = JSON.parse(caught.body) as {
          error?: string
          message?: string
          status?: number
        }
        if (parsed.error === 'Bad Gateway' || caught.status === 502) {
          return 'Usage and teaching information are temporarily unavailable. Please try again.'
        }
        if (parsed.message && parsed.message.trim().length > 0) {
          return parsed.message
        }
        if (parsed.error && parsed.error.trim().length > 0) {
          return parsed.error
        }
      } catch {
        if (!caught.body.trim().startsWith('{')) {
          return caught.body
        }
      }
    }
    return `Request failed (${caught.status}).`
  }
  if (caught instanceof Error) {
    return caught.message
  }
  return 'Unable to load Instructor Dashboard.'
}

export function useInstructorDashboardPage(
  selectedServerId: string | null,
  onSelectServerId: (serverId: string) => void,
): UseInstructorDashboardPageResult {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const serversQuery = useAccessibleStudyServersQuery()
  const servers = serversQuery.data ?? []
  const [dashboard, setDashboard] = useState<InstructorDashboard | null>(null)
  const [isOwner, setIsOwner] = useState(false)
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestKey =
    selectedServerId && userId ? `${selectedServerId}:${userId}:${reloadToken}` : null
  const isLoading =
    serversQuery.isLoading || (requestKey !== null && loadedKey !== requestKey)

  useEffect(() => {
    if (!serversQuery.isLoading && serversQuery.data && serversQuery.data.length > 0 && !selectedServerId) {
      onSelectServerId(serversQuery.data[0].id)
    }
  }, [onSelectServerId, selectedServerId, serversQuery.data, serversQuery.isLoading])

  useEffect(() => {
    if (!selectedServerId || !userId || requestKey === null) {
      return
    }

    let cancelled = false

    void (async () => {
      setAccessDenied(false)
      setError(null)
      setDashboard(null)
      setIsOwner(false)

      try {
        const [dashboardData, serverDetails] = await Promise.all([
          fetchInstructorDashboard(selectedServerId),
          fetchStudyServerDetails(selectedServerId),
        ])

        if (cancelled) {
          return
        }

        setDashboard(dashboardData)
        setIsOwner(serverDetails.ownerRole.userId === userId)

        setLoadedKey(requestKey)
      } catch (caught) {
        if (cancelled) {
          return
        }

        if (caught instanceof ApiError && caught.status === 403) {
          setAccessDenied(true)
        }
        setError(accessErrorMessage(caught))
        setLoadedKey(requestKey)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [requestKey, selectedServerId, userId])

  const refresh = useCallback(async () => {
    setReloadToken((current) => current + 1)
  }, [])

  return {
    servers,
    selectedServerId,
    setSelectedServerId: onSelectServerId,
    dashboard,
    isOwner,
    isLoading,
    accessDenied,
    error,
    refresh,
  }
}
