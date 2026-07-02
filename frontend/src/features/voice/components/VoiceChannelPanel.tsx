import { useAuthStore } from '../../../stores/auth-store'

import { useVoiceChannel } from '../hooks/use-voice-channel'

type VoiceChannelPanelProps = {
  channelId: string
  channelLabel: string
}

export function VoiceChannelPanel({ channelId, channelLabel }: VoiceChannelPanelProps) {
  const currentUserId = useAuthStore((state) => state.user?.id)
  const voice = useVoiceChannel(channelId)
  const isConnected = voice.status === 'connected'

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <header className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">Voice</p>
        <div className="mt-1 flex items-center justify-between gap-3">
          <h2 className="text-base font-semibold text-app-text">&gt; {channelLabel}</h2>
          <VoiceStatusBadge status={voice.status} />
        </div>
      </header>

      <div className="flex min-h-0 flex-1 flex-col gap-4 p-4">
        {voice.error ? (
          <p className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
            {voice.error}
          </p>
        ) : null}

        <div className="rounded-xl border border-app-border bg-app-surface p-4">
          <h3 className="text-sm font-semibold text-app-text">Room controls</h3>
          <p className="mt-1 text-sm text-app-muted">
            Join with your microphone to speak with other members in this voice channel.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            {!isConnected ? (
              <button
                type="button"
                disabled={voice.isBusy || voice.status === 'connecting'}
                onClick={() => void voice.joinVoice()}
                className="rounded-lg bg-app-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              >
                {voice.status === 'connecting' ? 'Connecting…' : 'Join voice'}
              </button>
            ) : (
              <>
                <button
                  type="button"
                  disabled={voice.isBusy}
                  onClick={() => void voice.toggleMute()}
                  className="rounded-lg border border-app-border px-4 py-2 text-sm text-app-text hover:bg-app-elevated disabled:opacity-60"
                >
                  {voice.isMuted ? 'Unmute' : 'Mute'}
                </button>
                <button
                  type="button"
                  disabled={voice.isBusy}
                  onClick={() => void voice.leaveVoice()}
                  className="rounded-lg border border-red-500/50 px-4 py-2 text-sm text-red-200 hover:bg-red-500/10 disabled:opacity-60"
                >
                  Leave voice
                </button>
              </>
            )}
          </div>
        </div>

        <div className="rounded-xl border border-app-border bg-app-surface p-4">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-semibold text-app-text">Connected members</h3>
            <button
              type="button"
              onClick={() => void voice.refreshPresences()}
              className="text-xs text-app-muted hover:text-app-text"
            >
              Refresh
            </button>
          </div>
          <ul className="mt-3 space-y-2">
            {voice.presences.length === 0 ? (
              <li className="text-sm text-app-muted">No one is in voice yet.</li>
            ) : (
              voice.presences.map((presence) => (
                <li
                  key={presence.memberUserId}
                  className="flex items-center justify-between rounded-lg border border-app-border px-3 py-2 text-sm"
                >
                  <span className="text-app-text">
                    {presence.memberUserId === currentUserId
                      ? 'You'
                      : presence.memberUserId.slice(0, 8)}
                  </span>
                  <span className="text-xs text-app-muted">
                    {presence.canSpeak ? 'Speak' : 'Listen'} · {presence.canListen ? 'Hear' : 'Muted'}
                  </span>
                </li>
              ))
            )}
          </ul>
        </div>
      </div>
    </section>
  )
}

function VoiceStatusBadge({ status }: { status: string }) {
  const label =
    status === 'connected'
      ? 'In voice'
      : status === 'connecting'
        ? 'Connecting…'
        : status === 'error'
          ? 'Error'
          : 'Not connected'

  return (
    <span className="rounded-full border border-app-border px-2 py-0.5 text-xs text-app-muted">
      {label}
    </span>
  )
}
