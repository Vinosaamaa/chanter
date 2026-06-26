import { useCallback, useEffect, useMemo, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { fetchCohortOfficeHoursAccess } from '../access-api'
import {
  admitNextOfficeHoursLearner,
  endOfficeHoursSession,
  joinOfficeHoursWaitlist,
  listOfficeHoursSessions,
  listOfficeHoursWaitlist,
  scheduleOfficeHours,
} from '../office-hours-api'
import type {
  OfficeHoursSession,
  OfficeHoursWaitlistEntry,
} from '../support-operations-types'

type UseOfficeHoursPanelResult = {
  sessions: OfficeHoursSession[]
  activeSession: OfficeHoursSession | null
  waitlist: OfficeHoursWaitlistEntry[]
  isLoading: boolean
  accessDenied: boolean
  canSchedule: boolean
  canJoin: boolean
  canManage: boolean
  error: string | null
  actionMessage: string | null
  isBusy: boolean
  refresh: () => Promise<void>
  scheduleSession: () => Promise<void>
  joinWaitlist: () => Promise<void>
  admitNext: () => Promise<void>
  endSession: () => Promise<void>
}

function accessErrorMessage(caught: unknown): string {
  if (caught instanceof ApiError) {
    if (caught.status === 403) {
      return 'You do not have permission to access Office Hours for this cohort.'
    }
    if (caught.body && caught.body.trim().length > 0) {
      return caught.body
    }
    return `Request failed (${caught.status}).`
  }
  if (caught instanceof Error) {
    return caught.message
  }
  return 'Unable to load Office Hours.'
}

function isActiveSession(session: OfficeHoursSession): boolean {
  return session.status === 'SCHEDULED' || session.status === 'LIVE'
}

export function useOfficeHoursPanel(cohortId: string | undefined): UseOfficeHoursPanelResult {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const [sessions, setSessions] = useState<OfficeHoursSession[]>([])
  const [waitlist, setWaitlist] = useState<OfficeHoursWaitlistEntry[]>([])
  const [canSchedule, setCanSchedule] = useState(false)
  const [canJoin, setCanJoin] = useState(false)
  const [canManage, setCanManage] = useState(false)
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [isBusy, setIsBusy] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  const requestKey = cohortId && userId ? `${cohortId}:${userId}:${reloadToken}` : null
  const isLoading = requestKey !== null && loadedKey !== requestKey

  const activeSession = useMemo(
    () => sessions.find((session) => isActiveSession(session)) ?? null,
    [sessions],
  )

  useEffect(() => {
    if (!cohortId || !userId || requestKey === null) {
      return
    }

    let cancelled = false

    void (async () => {
      try {
        const access = await fetchCohortOfficeHoursAccess(cohortId)
        if (cancelled) {
          return
        }

        setCanSchedule(access.canScheduleOfficeHours)
        setCanJoin(access.canJoinOfficeHours)
        setCanManage(access.canManageOfficeHours)
        setAccessDenied(false)
        setError(null)
        setActionMessage(null)

        const hasAccess =
          access.canScheduleOfficeHours ||
          access.canJoinOfficeHours ||
          access.canManageOfficeHours

        if (!hasAccess) {
          setAccessDenied(true)
          setSessions([])
          setWaitlist([])
          setLoadedKey(requestKey)
          return
        }

        const list = await listOfficeHoursSessions(cohortId, userId)
        if (cancelled) {
          return
        }

        setSessions(list.officeHoursSessions)

        const active = list.officeHoursSessions.find((session) => isActiveSession(session))
        if (active && access.canManageOfficeHours) {
          const waitlistList = await listOfficeHoursWaitlist(active.id, userId)
          if (!cancelled) {
            setWaitlist(waitlistList.waitlistEntries)
          }
        } else {
          setWaitlist([])
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

  const loadWaitlist = useCallback(
    async (sessionId: string) => {
      if (!userId || !canManage) {
        return
      }

      const list = await listOfficeHoursWaitlist(sessionId, userId)
      setWaitlist(list.waitlistEntries)
    },
    [canManage, userId],
  )

  const scheduleSession = useCallback(async () => {
    if (!cohortId || !userId || !canSchedule) {
      return
    }

    setIsBusy(true)
    setError(null)
    setActionMessage(null)

    try {
      const startsAt = new Date(Date.now() - 5 * 60 * 1000).toISOString()
      const endsAt = new Date(Date.now() + 60 * 60 * 1000).toISOString()
      const session = await scheduleOfficeHours(cohortId, userId, startsAt, endsAt)
      setSessions((current) => [session, ...current.filter((item) => item.id !== session.id)])
      setWaitlist([])
      setActionMessage(`Office Hours scheduled (${session.status}).`)
    } catch (caught) {
      setError(accessErrorMessage(caught))
    } finally {
      setIsBusy(false)
    }
  }, [canSchedule, cohortId, userId])

  const joinWaitlist = useCallback(async () => {
    if (!userId || !canJoin || !activeSession) {
      return
    }

    setIsBusy(true)
    setError(null)
    setActionMessage(null)

    try {
      await joinOfficeHoursWaitlist(activeSession.id, userId)
      await loadWaitlist(activeSession.id)
      setActionMessage('Joined the Office Hours waitlist.')
    } catch (caught) {
      setError(accessErrorMessage(caught))
    } finally {
      setIsBusy(false)
    }
  }, [activeSession, canJoin, loadWaitlist, userId])

  const admitNext = useCallback(async () => {
    if (!userId || !canManage || !activeSession) {
      return
    }

    setIsBusy(true)
    setError(null)
    setActionMessage(null)

    try {
      await admitNextOfficeHoursLearner(activeSession.id, userId)
      await loadWaitlist(activeSession.id)
      setActionMessage('Admitted the next learner from the waitlist.')
    } catch (caught) {
      setError(accessErrorMessage(caught))
    } finally {
      setIsBusy(false)
    }
  }, [activeSession, canManage, loadWaitlist, userId])

  const endSession = useCallback(async () => {
    if (!userId || !canManage || !activeSession) {
      return
    }

    setIsBusy(true)
    setError(null)
    setActionMessage(null)

    try {
      const ended = await endOfficeHoursSession(activeSession.id, userId)
      setSessions((current) =>
        current.map((session) => (session.id === ended.id ? ended : session)),
      )
      setWaitlist([])
      setActionMessage('Office Hours session ended.')
    } catch (caught) {
      setError(accessErrorMessage(caught))
    } finally {
      setIsBusy(false)
    }
  }, [activeSession, canManage, userId])

  return {
    sessions,
    activeSession,
    waitlist,
    isLoading,
    accessDenied,
    canSchedule,
    canJoin,
    canManage,
    error,
    actionMessage,
    isBusy,
    refresh,
    scheduleSession,
    joinWaitlist,
    admitNext,
    endSession,
  }
}
