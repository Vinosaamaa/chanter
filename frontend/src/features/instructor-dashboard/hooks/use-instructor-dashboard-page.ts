import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { useAccessibleStudyServersQuery } from '../../shell/hooks/use-shell-queries'

import {
  fetchInstructorDashboard,
  fetchStudyServerDetails,
  updateSaasPlan,
} from '../instructor-dashboard-api'
import type { InstructorDashboard, SaasPlanTier } from '../instructor-dashboard-types'

type UseInstructorDashboardPageResult = {
  servers: Array<{ id: string; name: string }>
  selectedServerId: string | null
  setSelectedServerId: (serverId: string) => void
  dashboard: InstructorDashboard | null
  isOwner: boolean
  planTierDraft: SaasPlanTier
  setPlanTierDraft: (tier: SaasPlanTier) => void
  isLoading: boolean
  isUpdatingPlan: boolean
  accessDenied: boolean
  error: string | null
  actionMessage: string | null
  refresh: () => Promise<void>
  savePlan: () => Promise<void>
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
          return 'Instructor Dashboard is temporarily unavailable. Ensure analytics-service (8086) is running and restart the gateway if it was started first.'
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

function isSaasPlanTier(value: string): value is SaasPlanTier {
  return value === 'STARTER' || value === 'PRO' || value === 'ORGANIZATION'
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
  const [planTierDraft, setPlanTierDraft] = useState<SaasPlanTier>('STARTER')
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [isUpdatingPlan, setIsUpdatingPlan] = useState(false)
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
      setActionMessage(null)
      setDashboard(null)

      try {
        const [dashboardData, serverDetails] = await Promise.all([
          fetchInstructorDashboard(selectedServerId, userId),
          fetchStudyServerDetails(selectedServerId),
        ])

        if (cancelled) {
          return
        }

        setDashboard(dashboardData)
        setIsOwner(serverDetails.ownerRole.userId === userId)

        const tier = isSaasPlanTier(dashboardData.planTier)
          ? dashboardData.planTier
          : isSaasPlanTier(serverDetails.planTier)
            ? serverDetails.planTier
            : 'STARTER'
        setPlanTierDraft(tier)
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

  const savePlan = useCallback(async () => {
    if (!selectedServerId || !userId || !isOwner || isUpdatingPlan) {
      return
    }

    setIsUpdatingPlan(true)
    setError(null)
    setActionMessage(null)

    try {
      const saved = await updateSaasPlan(selectedServerId, planTierDraft)
      setPlanTierDraft(saved.planTier)
      setActionMessage(
        `Plan updated to ${saved.planTier} (${saved.aiInvocationLimit} AI invocations per month).`,
      )
      await refresh()
    } catch (caught) {
      setError(accessErrorMessage(caught))
    } finally {
      setIsUpdatingPlan(false)
    }
  }, [isOwner, isUpdatingPlan, planTierDraft, refresh, selectedServerId, userId])

  return {
    servers,
    selectedServerId,
    setSelectedServerId: onSelectServerId,
    dashboard,
    isOwner,
    planTierDraft,
    setPlanTierDraft,
    isLoading,
    isUpdatingPlan,
    accessDenied,
    error,
    actionMessage,
    refresh,
    savePlan,
  }
}
