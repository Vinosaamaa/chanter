// Hosted test client using the same real SDK and user-issued join tokens as the app.
import { LocalAudioTrack, Room, RoomEvent } from 'livekit-client'
import { audioStatsAccumulator } from './moderation-audio-stats.mjs'
const room = new Room()
const audioStats = audioStatsAccumulator()
const status = document.getElementById('status')!
const peers: RTCPeerConnection[] = []
const NativePeer = window.RTCPeerConnection
window.RTCPeerConnection = class extends NativePeer { constructor(configuration?: RTCConfiguration) { super(configuration); peers.push(this) } }
let audio: AudioContext | undefined
let oscillator: OscillatorNode | undefined
let signal: MediaStreamAudioDestinationNode | undefined
room.on(RoomEvent.Disconnected, () => { status.textContent = 'Disconnected' })
room.on(RoomEvent.TrackSubscribed, track => { if (track.kind === 'audio') { const element = track.attach(); element.autoplay = true; document.body.append(element) } })
const proof = {
  async connect(url: string, token: string, publish: boolean) {
    await room.connect(url, token, { peerConnectionTimeout: 20000, websocketTimeout: 10000 })
    if (publish) {
      audio = new AudioContext(); oscillator = audio.createOscillator(); signal = audio.createMediaStreamDestination()
      oscillator.frequency.value = 440; oscillator.connect(signal); oscillator.start(); await audio.resume()
      let deadline: ReturnType<typeof setTimeout> | undefined
      try {
        await Promise.race([
          room.localParticipant.publishTrack(new LocalAudioTrack(signal.stream.getAudioTracks()[0]), { name: 'synthetic-moderation-proof' }),
          new Promise<never>((_, reject) => { deadline = setTimeout(() => reject({ reason: -1 }), 30000) }),
        ])
      } finally { clearTimeout(deadline) }
    }
    status.textContent = 'Connected'
  },
  async stats() {
    for (const peer of peers) audioStats.record(peer, (await peer.getStats()).values())
    return { ...audioStats.totals(), connected: room.state === 'connected', remoteParticipants: room.remoteParticipants.size }
  },
  async disconnect() { oscillator?.stop(); signal?.stream.getTracks().forEach(track => track.stop()); await audio?.close(); await room.disconnect() },
}
Object.assign(window, { moderationAudio: proof })
