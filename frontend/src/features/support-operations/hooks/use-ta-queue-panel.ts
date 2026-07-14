import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { fetchPublicProfiles } from '../../friends/friends-api'
import type { PublicUserProfile } from '../../friends/types'
import type { TaQueueItem } from '../../questions/support-question-types'
import { fetchCohortTaQueueAccess } from '../access-api'
import { cancelTaQueueItem, listTaQueueItems, pickupTaQueueItem, resolveTaQueueItem } from '../ta-queue-api'

type UseTaQueuePanelResult = {
  items: TaQueueItem[]
  profilesById: Record<string, PublicUserProfile>
  isLoading: boolean
  accessDenied: boolean
  canManage: boolean
  error: string | null
  actionMessage: string | null
  actingItemId: string | null
  refresh: () => Promise<void>
  pickupItem: (itemId: string) => Promise<void>
  resolveItem: (itemId: string) => Promise<void>
  cancelItem: (itemId: string) => Promise<void>
}

function accessErrorMessage(caught: unknown): string {
  if (caught instanceof ApiError) {
    if (caught.status === 403) {
      return 'You do not have permission to manage the TA queue for this cohort.'
    }
    if (caught.body && caught.body.trim().length > 0) {
      return caught.body
    }
    return `Request failed (${caught.status}).`
  }
  if (caught instanceof Error) {
    return caught.message
  }
  return 'Unable to load the TA queue.'
}

export function useTaQueuePanel(cohortId: string | undefined): UseTaQueuePanelResult {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const [items, setItems] = useState<TaQueueItem[]>([])
  const [canManage, setCanManage] = useState(false)
  const [profilesById, setProfilesById] = useState<Record<string, PublicUserProfile>>({})
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [actingItemId, setActingItemId] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  const requestKey = cohortId && userId ? `${cohortId}:${userId}:${reloadToken}` : null
  const hasActiveData = requestKey !== null && loadedKey === requestKey
  const isLoading = requestKey !== null && !hasActiveData

  useEffect(() => {
    if (!cohortId || !userId || requestKey === null) {
      return
    }

    let cancelled = false

    void (async () => {
      try {
        const access = await fetchCohortTaQueueAccess(cohortId)
        if (cancelled) {
          return
        }

        setCanManage(access.canManageTaQueue)
        setAccessDenied(false)
        setError(null)
        setActionMessage(null)

        if (!access.canManageTaQueue) {
          setAccessDenied(true)
          setItems([])
          setLoadedKey(requestKey)
          return
        }

        const list = await listTaQueueItems(cohortId)
        if (cancelled) {
          return
        }

        setItems(list.taQueueItems)
        const profileIds = Array.from(new Set(list.taQueueItems.map((item) => item.learnerUserId)))
        if (profileIds.length > 0) {
          const profiles = await fetchPublicProfiles(profileIds)
          if (cancelled) return
          setProfilesById(Object.fromEntries(profiles.profiles.map((profile) => [profile.userId, profile])))
        } else {
          setProfilesById({})
        }
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
  }, [cohortId, requestKey, userId])

  const refresh = useCallback(async () => {
    setReloadToken((current) => current + 1)
  }, [])

  const pickupItem = useCallback(
    async (itemId: string) => {
      if (!cohortId || !userId || !canManage) {
        return
      }

      setActingItemId(itemId)
      setError(null)
      setActionMessage(null)

      try {
        const updated = await pickupTaQueueItem(cohortId, itemId)
        setItems((current) => current.map((item) => (item.id === updated.id ? updated : item)))
        setActionMessage('Picked up queue item.')
      } catch (caught) {
        setError(accessErrorMessage(caught))
      } finally {
        setActingItemId(null)
      }
    },
    [canManage, cohortId, userId],
  )

  const resolveItem = useCallback(
    async (itemId: string) => {
      if (!cohortId || !userId || !canManage) {
        return
      }

      setActingItemId(itemId)
      setError(null)
      setActionMessage(null)

      try {
        const updated = await resolveTaQueueItem(cohortId, itemId)
        setItems((current) => current.filter((item) => item.id !== updated.id))
        setActionMessage('Resolved queue item.')
      } catch (caught) {
        setError(accessErrorMessage(caught))
      } finally {
        setActingItemId(null)
      }
    },
    [canManage, cohortId, userId],
  )

  const cancelItem = useCallback(
    async (itemId: string) => {
      if (!cohortId || !userId || !canManage) return
      setActingItemId(itemId)
      setError(null)
      setActionMessage(null)
      try {
        const updated = await cancelTaQueueItem(cohortId, itemId)
        setItems((current) => current.filter((item) => item.id !== updated.id))
        setActionMessage('Cancelled queue item.')
      } catch (caught) {
        setError(accessErrorMessage(caught))
      } finally {
        setActingItemId(null)
      }
    },
    [canManage, cohortId, userId],
  )

  return {
    items: hasActiveData ? items : [],
    profilesById: hasActiveData ? profilesById : {},
    isLoading: requestKey ? isLoading : false,
    accessDenied: hasActiveData ? accessDenied : false,
    canManage: hasActiveData ? canManage : false,
    error,
    actionMessage,
    actingItemId,
    refresh,
    pickupItem,
    resolveItem,
    cancelItem,
  }
}
