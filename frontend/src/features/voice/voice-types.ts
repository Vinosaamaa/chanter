export type VoicePresence = {
  channelId: string
  memberUserId: string
  canSpeak: boolean
  canListen: boolean
}

export type VoiceMediaToken = {
  roomName: string
  serverUrl: string
  participantToken: string
  canSpeak: boolean
  canListen: boolean
}

export type VoiceConnectionStatus = 'idle' | 'connecting' | 'connected' | 'error'
