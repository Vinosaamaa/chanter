export type FriendSummary = {
  friendUserId: string
  friendsSince: string
}

export type FriendsListResponse = {
  friends: FriendSummary[]
}

export type DirectMessage = {
  id: string
  senderUserId: string
  recipientUserId: string
  body: string
  sentAt: string
  clientMessageId?: string
}

export type DirectMessageListResponse = {
  messages: DirectMessage[]
}

export type FriendPresenceStatus = 'online' | 'offline'

export type SocialRealtimeMessage =
  | {
      type: 'social_subscribed'
    }
  | {
      type: 'presence_changed'
      userId: string
      status: FriendPresenceStatus
    }
  | {
      type: 'dm_message'
      payload: DirectMessage
    }
  | {
      type: 'error'
      code: string
      message: string
    }
