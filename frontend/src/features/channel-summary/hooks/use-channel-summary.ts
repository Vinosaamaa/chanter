import { useMutation } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'

import { generateChannelSummary } from '../channel-summary-api'
import type { ChannelSummary } from '../channel-summary-types'

export function useChannelSummary(channelId: string | undefined, windowDays = 7) {
  const { mutate, reset, data, isPending, isSuccess, error } = useMutation({
    mutationFn: () => generateChannelSummary(channelId!, windowDays),
  })

  useEffect(() => {
    reset()
  }, [channelId, windowDays, reset])

  const generate = useCallback(() => {
    if (!channelId) {
      return
    }
    mutate()
  }, [channelId, mutate])

  return {
    summary: data as ChannelSummary | undefined,
    isGenerating: isPending,
    error: error instanceof Error ? error.message : null,
    generate,
    hasGenerated: isSuccess,
  }
}
