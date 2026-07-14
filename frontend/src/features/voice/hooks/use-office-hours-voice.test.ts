import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchOfficeHoursMediaToken } from '../voice-api'

import { useOfficeHoursVoice } from './use-office-hours-voice'

const liveKit = vi.hoisted(() => ({
  status: 'idle' as const,
  error: null,
  isMuted: true,
  connect: vi.fn(),
  disconnect: vi.fn(),
  toggleMute: vi.fn(),
}))

vi.mock('../voice-api', () => ({
  fetchOfficeHoursMediaToken: vi.fn(),
}))

vi.mock('./use-livekit-room', () => ({
  useLiveKitRoom: () => liveKit,
}))

const mockedFetchToken = vi.mocked(fetchOfficeHoursMediaToken)

function token(canSpeak: boolean) {
  return {
    roomName: 'office-hours-session-1',
    serverUrl: 'ws://livekit.test',
    participantToken: canSpeak ? 'speaker-token' : 'listener-token',
    canSpeak,
    canListen: true,
  }
}

describe('useOfficeHoursVoice', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    liveKit.connect.mockResolvedValue(true)
    liveKit.disconnect.mockResolvedValue(undefined)
  })

  it('connects a learner with listener-only permissions from the server token', async () => {
    mockedFetchToken.mockResolvedValue(token(false))
    const { result } = renderHook(() => useOfficeHoursVoice('session-1'))

    await act(async () => {
      await result.current.joinVoice()
    })

    expect(mockedFetchToken).toHaveBeenCalledWith('session-1')
    expect(liveKit.connect).toHaveBeenCalledWith(token(false))
    expect(result.current.canSpeak).toBe(false)
  })

  it('refreshes the LiveKit token after speaking access changes', async () => {
    mockedFetchToken.mockResolvedValueOnce(token(false)).mockResolvedValueOnce(token(true))
    const { result } = renderHook(() => useOfficeHoursVoice('session-1'))

    await act(async () => {
      await result.current.joinVoice()
      await result.current.refreshPermissions()
    })

    expect(liveKit.connect).toHaveBeenLastCalledWith(token(true))
    expect(result.current.canSpeak).toBe(true)
  })

  it('disconnects media locally when leaving Office Hours', async () => {
    const { result } = renderHook(() => useOfficeHoursVoice('session-1'))

    await act(async () => {
      await result.current.leaveVoice()
    })

    expect(liveKit.disconnect).toHaveBeenCalledOnce()
    expect(result.current.canSpeak).toBe(false)
  })
})
