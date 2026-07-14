import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../../stores/auth-store'
import { fetchPublicProfiles } from '../../friends/friends-api'
import { fetchCohortOfficeHoursAccess } from '../access-api'
import {
  joinOfficeHoursSession,
  listOfficeHoursParticipants,
  listOfficeHoursSessions,
} from '../office-hours-api'

import { useOfficeHoursPanel } from './use-office-hours-panel'

vi.mock('../../friends/friends-api', () => ({
  fetchPublicProfiles: vi.fn(),
}))

vi.mock('../access-api', () => ({
  fetchCohortOfficeHoursAccess: vi.fn(),
}))

vi.mock('../office-hours-api', () => ({
  cancelOfficeHoursSession: vi.fn(),
  endOfficeHoursSession: vi.fn(),
  joinOfficeHoursSession: vi.fn(),
  leaveOfficeHoursSession: vi.fn(),
  listOfficeHoursParticipants: vi.fn(),
  listOfficeHoursSessions: vi.fn(),
  scheduleOfficeHours: vi.fn(),
  startOfficeHoursSession: vi.fn(),
  updateOfficeHoursHand: vi.fn(),
  updateOfficeHoursSession: vi.fn(),
  updateOfficeHoursSpeaking: vi.fn(),
}))

const mockedFetchAccess = vi.mocked(fetchCohortOfficeHoursAccess)
const mockedFetchProfiles = vi.mocked(fetchPublicProfiles)
const mockedJoinSession = vi.mocked(joinOfficeHoursSession)
const mockedListParticipants = vi.mocked(listOfficeHoursParticipants)
const mockedListSessions = vi.mocked(listOfficeHoursSessions)

describe('useOfficeHoursPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 'owner-1', email: 'owner@example.com', displayName: 'Owner' },
    })
    mockedFetchProfiles.mockResolvedValue({ profiles: [] })
    mockedListParticipants.mockResolvedValue({ participants: [] })
  })

  afterEach(() => {
    useAuthStore.getState().clearSession()
  })

  it('hides the previous Cohort sessions and capabilities while a new Cohort loads', async () => {
    mockedFetchAccess.mockResolvedValueOnce({
      cohortId: 'cohort-1',
      courseId: 'course-1',
      studyServerId: 'server-1',
      canScheduleOfficeHours: true,
      canJoinOfficeHours: false,
      canManageOfficeHours: true,
    })
    mockedListSessions.mockResolvedValueOnce({
      officeHoursSessions: [{
        id: 'session-1',
        cohortId: 'cohort-1',
        voiceChannelId: 'voice-1',
        scheduledByUserId: 'owner-1',
        startsAt: '2026-07-20T17:00:00Z',
        endsAt: '2026-07-20T18:00:00Z',
        status: 'SCHEDULED',
        createdAt: '2026-07-13T17:00:00Z',
      }],
    })

    const { result, rerender } = renderHook(
      ({ cohortId }) => useOfficeHoursPanel(cohortId),
      { initialProps: { cohortId: 'cohort-1' } },
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.sessions).toHaveLength(1)
    expect(result.current.canManage).toBe(true)

    mockedFetchAccess.mockReturnValueOnce(new Promise(() => {}))
    rerender({ cohortId: 'cohort-2' })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.sessions).toEqual([])
    expect(result.current.canManage).toBe(false)
  })

  it('keeps a successful join when the participant poll refreshes separately', async () => {
    mockedFetchAccess.mockResolvedValue({
      cohortId: 'cohort-1',
      courseId: 'course-1',
      studyServerId: 'server-1',
      canScheduleOfficeHours: false,
      canJoinOfficeHours: true,
      canManageOfficeHours: false,
    })
    mockedListSessions.mockResolvedValue({
      officeHoursSessions: [{
        id: 'session-live',
        cohortId: 'cohort-1',
        voiceChannelId: 'voice-1',
        scheduledByUserId: 'instructor-1',
        startsAt: '2026-07-20T17:00:00Z',
        endsAt: '2026-07-20T18:00:00Z',
        status: 'LIVE',
        createdAt: '2026-07-13T17:00:00Z',
      }],
    })
    const participant = {
      sessionId: 'session-live',
      userId: 'owner-1',
      canSpeak: false,
      handRaised: false,
      joinedAt: '2026-07-20T17:01:00Z',
      updatedAt: '2026-07-20T17:01:00Z',
    }
    mockedJoinSession.mockResolvedValue(participant)
    const { result } = renderHook(() => useOfficeHoursPanel('cohort-1'))
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    let joined = null
    await act(async () => {
      joined = await result.current.joinSession()
    })

    expect(joined).toEqual(participant)
    expect(result.current.currentParticipant).toEqual(participant)
    expect(mockedListParticipants).toHaveBeenCalledOnce()
  })
})
