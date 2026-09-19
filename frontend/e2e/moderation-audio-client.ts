// Hosted test client using the same real SDK and user-issued join tokens as the app.
import { LocalAudioTrack, Room, RoomEvent } from 'livekit-client'
const room = new Room()
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
    await room.connect(url, token)
    if (publish) {
      audio = new AudioContext(); oscillator = audio.createOscillator(); signal = audio.createMediaStreamDestination()
      oscillator.frequency.value = 440; oscillator.connect(signal); oscillator.start(); await audio.resume()
      await room.localParticipant.publishTrack(new LocalAudioTrack(signal.stream.getAudioTracks()[0]), { name: 'synthetic-moderation-proof' })
    }
    status.textContent = 'Connected'
  },
  async stats() {
    let sent = 0, received = 0, energy = 0
    for (const peer of peers) for (const item of (await peer.getStats()).values()) {
      if (item.kind !== 'audio') continue
      if (item.type === 'outbound-rtp') sent += item.bytesSent ?? 0
      if (item.type === 'inbound-rtp') { received += item.bytesReceived ?? 0; energy += item.totalAudioEnergy ?? 0 }
    }
    return { sent, received, energy, connected: room.state === 'connected', remoteParticipants: room.remoteParticipants.size }
  },
  async disconnect() { oscillator?.stop(); signal?.stream.getTracks().forEach(track => track.stop()); await audio?.close(); await room.disconnect() },
}
Object.assign(window, { moderationAudio: proof })
