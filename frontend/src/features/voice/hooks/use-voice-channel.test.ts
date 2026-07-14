import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  fetchVoiceChannelMediaToken,
  fetchVoicePresences,
  joinVoiceChannel,
  leaveVoiceChannel,
} from '../voice-api'

import { useVoiceChannel } from './use-voice-channel'

const liveKit = vi.hoisted(() => ({
  status: 'idle' as 'idle' | 'connected',
  error: null,
  isMuted: false,
  connect: vi.fn(),
  disconnect: vi.fn(),
  toggleMute: vi.fn(),
}))

vi.mock('../voice-api', () => ({
  fetchVoiceChannelMediaToken: vi.fn(),
  fetchVoicePresences: vi.fn(),
  joinVoiceChannel: vi.fn(),
  leaveVoiceChannel: vi.fn(),
}))

vi.mock('./use-livekit-room', () => ({
  useLiveKitRoom: () => liveKit,
}))

describe('useVoiceChannel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    liveKit.status = 'idle'
    liveKit.connect.mockResolvedValue(true)
    liveKit.disconnect.mockResolvedValue(undefined)
    vi.mocked(fetchVoiceChannelMediaToken).mockResolvedValue({
      roomName: 'voice-course-1',
      serverUrl: 'ws://livekit.test',
      participantToken: 'participant-token',
      canSpeak: true,
      canListen: true,
    })
    vi.mocked(fetchVoicePresences).mockResolvedValue([])
    vi.mocked(joinVoiceChannel).mockResolvedValue(undefined)
  })

  it('publishes presence only after LiveKit confirms the connection', async () => {
    const { result } = renderHook(() => useVoiceChannel('course-1', 'course'))

    await act(async () => {
      await result.current.joinVoice()
    })

    expect(liveKit.connect).toHaveBeenCalledOnce()
    expect(joinVoiceChannel).toHaveBeenCalledWith('course-1', 'course')
    expect(liveKit.connect.mock.invocationCallOrder[0])
      .toBeLessThan(vi.mocked(joinVoiceChannel).mock.invocationCallOrder[0])
  })

  it('does not publish presence when LiveKit cannot connect', async () => {
    liveKit.connect.mockResolvedValue(false)
    const { result } = renderHook(() => useVoiceChannel('course-1', 'course'))

    await act(async () => {
      await result.current.joinVoice()
    })

    expect(joinVoiceChannel).not.toHaveBeenCalled()
  })

  it('disconnects media when presence publication fails', async () => {
    vi.mocked(joinVoiceChannel).mockRejectedValue(new Error('Presence unavailable'))
    const { result } = renderHook(() => useVoiceChannel('course-1', 'course'))

    await act(async () => {
      await result.current.joinVoice()
    })

    expect(liveKit.disconnect).toHaveBeenCalledOnce()
    expect(result.current.error).toBe('Presence unavailable')
  })

  it('disconnects locally even when server-side leave fails', async () => {
    vi.mocked(leaveVoiceChannel).mockRejectedValue(new Error('Gateway unavailable'))
    const { result } = renderHook(() => useVoiceChannel('course-1', 'course'))

    await act(async () => {
      await result.current.leaveVoice()
    })

    expect(liveKit.disconnect).toHaveBeenCalledOnce()
    expect(result.current.error).toBe('Gateway unavailable')
  })

  it('cancels a pending join when the selected channel changes', async () => {
    let resolveToken: ((value: {
      roomName: string
      serverUrl: string
      participantToken: string
      canSpeak: boolean
      canListen: boolean
    }) => void) | undefined
    vi.mocked(fetchVoiceChannelMediaToken).mockImplementationOnce(() => new Promise((resolve) => {
      resolveToken = resolve
    }))
    const { result, rerender } = renderHook(
      ({ channelId }) => useVoiceChannel(channelId, 'course'),
      { initialProps: { channelId: 'course-1' } },
    )

    let pendingJoin: Promise<void>
    act(() => {
      pendingJoin = result.current.joinVoice()
    })
    rerender({ channelId: 'course-2' })
    await act(async () => {
      resolveToken?.({
        roomName: 'voice-course-1',
        serverUrl: 'ws://livekit.test',
        participantToken: 'stale-token',
        canSpeak: true,
        canListen: true,
      })
      await pendingJoin
    })

    expect(liveKit.connect).not.toHaveBeenCalled()
    expect(joinVoiceChannel).not.toHaveBeenCalled()
  })

  it('removes published presence and disconnects when unmounted', async () => {
    const { result, unmount } = renderHook(() => useVoiceChannel('course-1', 'course'))
    await act(async () => {
      await result.current.joinVoice()
    })

    unmount()

    expect(liveKit.disconnect).toHaveBeenCalled()
    expect(leaveVoiceChannel).toHaveBeenCalledWith('course-1', 'course')
  })

  it('surfaces presence loading failures instead of an empty room', async () => {
    vi.mocked(fetchVoicePresences).mockRejectedValue(new Error('Presence service unavailable'))
    const { result } = renderHook(() => useVoiceChannel('course-1', 'course'))

    await waitFor(() => expect(result.current.isLoadingPresences).toBe(false))

    expect(result.current.presenceError).toBe('Presence service unavailable')
  })
})
