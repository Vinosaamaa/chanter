import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'

import { formatDeltaPercent, formatSummaryMetricValue, generateChannelSummary } from './channel-summary-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

const mockedApiFetch = vi.mocked(apiFetch)

describe('channel-summary-api', () => {
  beforeEach(() => {
    mockedApiFetch.mockReset()
  })

  it('generateChannelSummary posts the channel id and window days', async () => {
    mockedApiFetch.mockResolvedValue({ channelId: 'channel-1', windowDays: 7 })

    await generateChannelSummary('channel-1', 7)

    expect(mockedApiFetch).toHaveBeenCalledWith('/api/v1/course-channels/channel-1/channel-summary', {
      method: 'POST',
      body: JSON.stringify({ windowDays: 7 }),
    })
  })

  it('formatSummaryMetricValue formats views and resolved percent', () => {
    expect(formatSummaryMetricValue('views', 3200)).toBe('3.2K')
    expect(formatSummaryMetricValue('resolvedPercent', 87)).toBe('87%')
    expect(formatSummaryMetricValue('questionsAsked', 48)).toBe('48')
  })

  it('formatDeltaPercent shows directional trend text', () => {
    expect(formatDeltaPercent(14)).toBe('↑ 14% vs last period')
    expect(formatDeltaPercent(-5)).toBe('↓ 5% vs last period')
  })
})
