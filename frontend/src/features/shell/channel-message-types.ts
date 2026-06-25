export type ChannelScope = 'study' | 'course'

export type ChannelMessage = {
  id: string
  channelId: string
  senderUserId: string
  body: string
  createdAt: string
}

export type ChannelMessageListResponse = {
  messages: ChannelMessage[]
}

export type RealtimeServerMessage =
  | {
      type: 'subscribed'
      channelId: string
      channelScope: 'STUDY_SERVER' | 'COURSE'
    }
  | {
      type: 'unsubscribed'
    }
  | {
      type: 'message'
      channelId: string
      channelScope: 'STUDY_SERVER' | 'COURSE'
      payload: ChannelMessage
    }
  | {
      type: 'error'
      code: string
      message: string
    }

export function toRealtimeChannelScope(scope: ChannelScope): 'STUDY_SERVER' | 'COURSE' {
  return scope === 'study' ? 'STUDY_SERVER' : 'COURSE'
}

export function messagesPath(scope: ChannelScope, channelId: string): string {
  return scope === 'study'
    ? `/api/v1/study-server-channels/${channelId}/messages`
    : `/api/v1/course-channels/${channelId}/messages`
}
