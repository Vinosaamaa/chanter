import { apiFetch } from '../../lib/api-client'

import type { ChannelSummary } from './channel-summary-types'

export async function generateChannelSummary(
  channelId: string,
  windowDays = 7,
): Promise<ChannelSummary> {
  return apiFetch<ChannelSummary>(`/api/v1/course-channels/${channelId}/channel-summary`, {
    method: 'POST',
    body: JSON.stringify({ windowDays }),
  })
}

export function formatSummaryMetricValue(metricKey: keyof ChannelSummary['metrics'], value: number): string {
  if (metricKey === 'views' && value >= 1000) {
    const thousands = value / 1000
    return `${thousands.toFixed(thousands >= 10 ? 0 : 1)}K`
  }
  if (metricKey === 'resolvedPercent') {
    return `${value}%`
  }
  return String(value)
}

export function formatDeltaPercent(deltaPercent: number): string {
  const sign = deltaPercent >= 0 ? '↑' : '↓'
  return `${sign} ${Math.abs(deltaPercent)}% vs last period`
}
