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

export type DmCallPhase =
  | 'idle'
  | 'outgoing_ringing'
  | 'incoming_ringing'
  | 'connecting'
  | 'in_call'
  | 'ended'

export type DmCallState = {
  phase: DmCallPhase
  callId: string | null
  peerUserId: string | null
  reason: string | null
}

export type SocialCallMessage =
  | {
      type: 'call_ringing'
      callId: string
      callerUserId: string
      calleeUserId: string
      direction: 'incoming' | 'outgoing'
    }
  | {
      type: 'call_accepted'
      callId: string
      callerUserId: string
      calleeUserId: string
    }
  | {
      type: 'call_busy'
      userId: string
    }
  | {
      type: 'call_ended'
      callId: string
      reason: string
    }
