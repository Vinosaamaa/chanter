import { useCallback, useEffect, useMemo, useState } from 'react'

import { fetchPublicProfiles } from '../../friends/friends-api'
import type { PublicUserProfile } from '../../friends/types'
import { ApiError } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/auth-store'
import { fetchCohortOfficeHoursAccess } from '../access-api'
import {
  cancelOfficeHoursSession,
  endOfficeHoursSession,
  joinOfficeHoursSession,
  leaveOfficeHoursSession,
  listOfficeHoursParticipants,
  listOfficeHoursSessions,
  scheduleOfficeHours,
  startOfficeHoursSession,
  updateOfficeHoursHand,
  updateOfficeHoursSession,
  updateOfficeHoursSpeaking,
} from '../office-hours-api'
import type { OfficeHoursScheduleInput } from '../office-hours-api'
import type { OfficeHoursParticipant, OfficeHoursSession } from '../support-operations-types'

export type UseOfficeHoursPanelResult = {
  sessions: OfficeHoursSession[]
  liveSession: OfficeHoursSession | null
  activeSession: OfficeHoursSession | null
  upcomingSessions: OfficeHoursSession[]
  participants: OfficeHoursParticipant[]
  currentParticipant: OfficeHoursParticipant | null
  profilesById: Record<string, PublicUserProfile>
  isLoading: boolean
  accessDenied: boolean
  canSchedule: boolean
  canJoin: boolean
  canManage: boolean
  error: string | null
  actionMessage: string | null
  isBusy: boolean
  refresh: () => Promise<void>
  scheduleSession: (input: OfficeHoursScheduleInput) => Promise<boolean>
  updateSession: (sessionId: string, input: OfficeHoursScheduleInput) => Promise<boolean>
  cancelSession: (sessionId: string) => Promise<boolean>
  startSession: (sessionId: string) => Promise<boolean>
  joinSession: () => Promise<OfficeHoursParticipant | null>
  setHandRaised: (raised: boolean) => Promise<boolean>
  grantSpeaking: (userId: string, canSpeak: boolean) => Promise<boolean>
  leaveSession: () => Promise<boolean>
  endSession: () => Promise<boolean>
}

function errorMessage(caught: unknown): string {
  if (caught instanceof ApiError) {
    if (caught.status === 403) return 'You do not have permission for this Office Hours action.'
    if (caught.body?.trim()) return caught.body
    return `Request failed (${caught.status}).`
  }
  return caught instanceof Error ? caught.message : 'Unable to update Office Hours.'
}

function sortSessions(sessions: OfficeHoursSession[]): OfficeHoursSession[] {
  return [...sessions].sort((left, right) => left.startsAt.localeCompare(right.startsAt))
}

function sameParticipants(left: OfficeHoursParticipant[], right: OfficeHoursParticipant[]): boolean {
  return left.length === right.length && left.every((participant, index) => {
    const other = right[index]
    return other !== undefined
      && participant.sessionId === other.sessionId
      && participant.userId === other.userId
      && participant.canSpeak === other.canSpeak
      && participant.handRaised === other.handRaised
      && participant.joinedAt === other.joinedAt
      && participant.updatedAt === other.updatedAt
  })
}

export function useOfficeHoursPanel(cohortId: string | undefined): UseOfficeHoursPanelResult {
  const userId = useAuthStore((state) => state.user?.id ?? null)
  const hasContext = Boolean(cohortId && userId)
  const [sessions, setSessions] = useState<OfficeHoursSession[]>([])
  const [sessionsCohortId, setSessionsCohortId] = useState<string | null>(null)
  const [participants, setParticipants] = useState<OfficeHoursParticipant[]>([])
  const [profilesById, setProfilesById] = useState<Record<string, PublicUserProfile>>({})
  const [canSchedule, setCanSchedule] = useState(false)
  const [canJoin, setCanJoin] = useState(false)
  const [canManage, setCanManage] = useState(false)
  const [accessCohortId, setAccessCohortId] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(Boolean(cohortId && userId))
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errorCohortId, setErrorCohortId] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [actionCohortId, setActionCohortId] = useState<string | null>(null)
  const [isBusy, setIsBusy] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  const hasCurrentAccess = hasContext && accessCohortId === cohortId
  const effectiveCanSchedule = hasCurrentAccess && canSchedule
  const effectiveCanJoin = hasCurrentAccess && canJoin
  const effectiveCanManage = hasCurrentAccess && canManage
  const visibleSessions = useMemo(
    () => hasContext && sessionsCohortId === cohortId ? sessions : [],
    [cohortId, hasContext, sessions, sessionsCohortId],
  )
  const liveSession = useMemo(
    () => visibleSessions.find((session) => session.status === 'LIVE') ?? null,
    [visibleSessions],
  )
  const upcomingSessions = useMemo(
    () => sortSessions(visibleSessions.filter((session) => session.status === 'SCHEDULED')),
    [visibleSessions],
  )
  const activeSession = liveSession ?? upcomingSessions[0] ?? null
  const sessionParticipants = useMemo(
    () => liveSession ? participants.filter((participant) => participant.sessionId === liveSession.id) : [],
    [liveSession, participants],
  )
  const currentParticipant = useMemo(
    () => sessionParticipants.find((participant) => participant.userId === userId) ?? null,
    [sessionParticipants, userId],
  )

  useEffect(() => {
    if (!cohortId || !userId) {
      return
    }
    let cancelled = false
    void (async () => {
      setIsLoading(true)
      setError(null)
      setActionMessage(null)
      try {
        const access = await fetchCohortOfficeHoursAccess(cohortId)
        if (cancelled) return
        setAccessCohortId(cohortId)
        setCanSchedule(access.canScheduleOfficeHours)
        setCanJoin(access.canJoinOfficeHours)
        setCanManage(access.canManageOfficeHours)
        const hasAccess = access.canScheduleOfficeHours || access.canJoinOfficeHours || access.canManageOfficeHours
        setAccessDenied(!hasAccess)
        if (!hasAccess) {
          setSessions([])
          setSessionsCohortId(cohortId)
          return
        }
        const response = await listOfficeHoursSessions(cohortId)
        if (!cancelled) {
          setSessions(sortSessions(response.officeHoursSessions))
          setSessionsCohortId(cohortId)
          setError(null)
        }
      } catch (caught) {
        if (!cancelled) {
          setAccessCohortId(cohortId)
          setCanSchedule(false)
          setCanJoin(false)
          setCanManage(false)
          setSessions([])
          setSessionsCohortId(cohortId)
          setAccessDenied(caught instanceof ApiError && caught.status === 403)
          setError(errorMessage(caught))
          setErrorCohortId(cohortId)
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [cohortId, reloadToken, userId])

  useEffect(() => {
    if (!liveSession) {
      return
    }
    let cancelled = false
    const load = async () => {
      try {
        const response = await listOfficeHoursParticipants(liveSession.id)
        if (!cancelled) {
          setParticipants((current) => sameParticipants(current, response.participants) ? current : response.participants)
        }
      } catch (caught) {
        if (!cancelled) {
          setError(errorMessage(caught))
          setErrorCohortId(cohortId ?? null)
        }
      }
    }
    void load()
    const intervalId = window.setInterval(() => void load(), 2_000)
    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [cohortId, liveSession])

  useEffect(() => {
    const ids = [...new Set(sessionParticipants.map((participant) => participant.userId))]
    if (ids.length === 0) {
      return
    }
    let cancelled = false
    void fetchPublicProfiles(ids).then((response) => {
      if (!cancelled) {
        setProfilesById(Object.fromEntries(response.profiles.map((profile) => [profile.userId, profile])))
      }
    }).catch(() => {
      if (!cancelled) setProfilesById({})
    })
    return () => { cancelled = true }
  }, [sessionParticipants])

  const refresh = useCallback(async () => {
    setReloadToken((current) => current + 1)
  }, [])

  const run = useCallback(async <T,>(action: () => Promise<T>, message: string): Promise<T | null> => {
    setIsBusy(true)
    setError(null)
    setErrorCohortId(cohortId ?? null)
    setActionMessage(null)
    setActionCohortId(cohortId ?? null)
    try {
      const result = await action()
      setActionMessage(message)
      return result
    } catch (caught) {
      setError(errorMessage(caught))
      return null
    } finally {
      setIsBusy(false)
    }
  }, [cohortId])

  const scheduleSession = useCallback(async (input: OfficeHoursScheduleInput) => {
    if (!cohortId || !effectiveCanSchedule) return false
    const created = await run(() => scheduleOfficeHours(cohortId, input), 'Office Hours scheduled.')
    if (!created) return false
    setSessions((current) => sortSessions([...current, created]))
    return true
  }, [cohortId, effectiveCanSchedule, run])

  const updateSession = useCallback(async (sessionId: string, input: OfficeHoursScheduleInput) => {
    if (!effectiveCanManage) return false
    const updated = await run(() => updateOfficeHoursSession(sessionId, input), 'Office Hours updated.')
    if (!updated) return false
    setSessions((current) => sortSessions(current.map((session) => session.id === updated.id ? updated : session)))
    return true
  }, [effectiveCanManage, run])

  const cancelSession = useCallback(async (sessionId: string) => {
    if (!effectiveCanManage) return false
    const cancelled = await run(() => cancelOfficeHoursSession(sessionId), 'Office Hours cancelled.')
    if (!cancelled) return false
    setSessions((current) => current.map((session) => session.id === cancelled.id ? cancelled : session))
    return true
  }, [effectiveCanManage, run])

  const startSession = useCallback(async (sessionId: string) => {
    if (!effectiveCanManage) return false
    const started = await run(() => startOfficeHoursSession(sessionId), 'Office Hours started.')
    if (!started) return false
    setSessions((current) => current.map((session) => session.id === started.id ? started : session))
    return true
  }, [effectiveCanManage, run])

  const joinSession = useCallback(async () => {
    if (!liveSession || (!effectiveCanJoin && !effectiveCanManage)) return null
    const participant = await run(() => joinOfficeHoursSession(liveSession.id), 'Joined Office Hours.')
    if (participant) {
      setParticipants((current) => [
        ...current.filter((item) => item.sessionId !== participant.sessionId || item.userId !== participant.userId),
        participant,
      ])
    }
    return participant
  }, [effectiveCanJoin, effectiveCanManage, liveSession, run])

  const setHandRaised = useCallback(async (raised: boolean) => {
    if (!liveSession || !currentParticipant) return false
    const participant = await run(
      () => updateOfficeHoursHand(liveSession.id, raised),
      raised ? 'Hand raised.' : 'Hand lowered.',
    )
    if (!participant) return false
    setParticipants((current) => current.map((item) => item.userId === participant.userId ? participant : item))
    return true
  }, [currentParticipant, liveSession, run])

  const grantSpeaking = useCallback(async (participantUserId: string, canSpeakValue: boolean) => {
    if (!liveSession || !effectiveCanManage) return false
    const participant = await run(
      () => updateOfficeHoursSpeaking(liveSession.id, participantUserId, canSpeakValue),
      canSpeakValue ? 'Speaking access granted.' : 'Speaking access removed.',
    )
    if (!participant) return false
    setParticipants((current) => current.map((item) => item.userId === participant.userId ? participant : item))
    return true
  }, [effectiveCanManage, liveSession, run])

  const leaveSession = useCallback(async () => {
    if (!liveSession || !currentParticipant) return false
    const left = await run(() => leaveOfficeHoursSession(liveSession.id), 'Left Office Hours.')
    if (left === null) return false
    setParticipants((current) => current.filter((item) => item.userId !== userId))
    return true
  }, [currentParticipant, liveSession, run, userId])

  const endSession = useCallback(async () => {
    if (!liveSession || !effectiveCanManage) return false
    const ended = await run(() => endOfficeHoursSession(liveSession.id), 'Office Hours ended.')
    if (!ended) return false
    setSessions((current) => current.map((session) => session.id === ended.id ? ended : session))
    setParticipants([])
    return true
  }, [effectiveCanManage, liveSession, run])

  return {
    sessions: visibleSessions,
    liveSession,
    activeSession,
    upcomingSessions,
    participants: sessionParticipants,
    currentParticipant,
    profilesById,
    isLoading: hasContext ? !hasCurrentAccess || isLoading : false,
    accessDenied: hasCurrentAccess && accessDenied,
    canSchedule: effectiveCanSchedule,
    canJoin: effectiveCanJoin,
    canManage: effectiveCanManage,
    error: errorCohortId === cohortId ? error : null,
    actionMessage: actionCohortId === cohortId ? actionMessage : null,
    isBusy,
    refresh,
    scheduleSession,
    updateSession,
    cancelSession,
    startSession,
    joinSession,
    setHandRaised,
    grantSpeaking,
    leaveSession,
    endSession,
  }
}
