import { useMutation } from '@tanstack/react-query'
import { useCallback } from 'react'

import { generateChannelSummary } from '../channel-summary-api'
import type { ChannelSummary } from '../channel-summary-types'

export function useChannelSummary(channelId: string | undefined, windowDays = 7) {
  const mutation = useMutation({
    mutationFn: () => generateChannelSummary(channelId!, windowDays),
  })

  const generate = useCallback(() => {
    if (!channelId) {
      return
    }
    mutation.mutate()
  }, [channelId, mutation])

  return {
    summary: mutation.data as ChannelSummary | undefined,
    isGenerating: mutation.isPending,
    error: mutation.error instanceof Error ? mutation.error.message : null,
    generate,
    hasGenerated: mutation.isSuccess,
  }
}
