export type ChannelSummaryMetric = {
  value: number
  deltaPercent: number
}

export type ChannelSummaryMetrics = {
  questionsAsked: ChannelSummaryMetric
  replies: ChannelSummaryMetric
  views: ChannelSummaryMetric
  resolvedPercent: ChannelSummaryMetric
}

export type ChannelSummaryTopicSection = {
  title: string
  summary: string
}

export type ChannelSummaryFollowUpsSection = {
  count: number
  summary: string
  questions: string[]
}

export type ChannelSummaryTextItemsSection = {
  summary: string
  items: string[]
}

export type ChannelSummaryDigest = {
  topTopics: ChannelSummaryTopicSection
  unansweredFollowUps: ChannelSummaryFollowUpsSection
  keyDecisions: ChannelSummaryTextItemsSection
  resourceLinks: ChannelSummaryTextItemsSection
}

export type ChannelSummaryTimelineEvent = {
  type: string
  title: string
  occurredAt: string
}

export type ChannelSummary = {
  channelId: string
  channelName: string
  windowDays: number
  windowStart: string
  windowEnd: string
  generatedAt: string
  metrics: ChannelSummaryMetrics
  digest: ChannelSummaryDigest
  timeline: ChannelSummaryTimelineEvent[]
}
