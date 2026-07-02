import { useOfficeHoursPanel } from '../hooks/use-office-hours-panel'
import { useOfficeHoursVoice } from '../../voice/hooks/use-office-hours-voice'
import type { OfficeHoursSession } from '../support-operations-types'

type OfficeHoursPanelProps = {
  courseTitle: string
  cohortName: string
  cohortId: string
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

export function OfficeHoursPanel({ courseTitle, cohortName, cohortId }: OfficeHoursPanelProps) {
  const officeHours = useOfficeHoursPanel(cohortId)

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <header className="border-b border-app-border px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Support operations
        </p>
        <h2 className="mt-1 text-xl font-semibold text-app-text">Office Hours</h2>
        <p className="mt-1 text-sm text-app-muted">
          {courseTitle} · {cohortName}
        </p>
      </header>

      <div className="flex-1 overflow-y-auto p-4">
        {officeHours.isLoading && <p className="text-sm text-app-muted">Loading Office Hours…</p>}

        {officeHours.accessDenied && (
          <p className="rounded-lg border border-app-border bg-app-surface px-4 py-3 text-sm text-app-muted">
            You are not enrolled in this cohort or lack permission to join Office Hours.
          </p>
        )}

        {officeHours.error && (
          <p
            role="status"
            aria-live="polite"
            className="mb-3 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200"
          >
            {officeHours.error}
          </p>
        )}

        {officeHours.actionMessage && (
          <p
            role="status"
            aria-live="polite"
            className="mb-3 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200"
          >
            {officeHours.actionMessage}
          </p>
        )}

        {!officeHours.isLoading && !officeHours.accessDenied && (
          <div className="flex flex-col gap-4">
            <div className="rounded-xl border border-app-border bg-app-surface p-4">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <h3 className="text-sm font-semibold text-app-text">Current session</h3>
                  {officeHours.activeSession ? (
                    <p className="mt-1 text-sm text-app-muted">
                      {officeHours.activeSession.status} until{' '}
                      {formatTimestamp(officeHours.activeSession.endsAt)}
                    </p>
                  ) : (
                    <p className="mt-1 text-sm text-app-muted">No live Office Hours session.</p>
                  )}
                </div>

                <div className="flex flex-wrap gap-2">
                  {officeHours.canSchedule && !officeHours.activeSession && (
                    <button
                      type="button"
                      disabled={officeHours.isBusy}
                      onClick={() => void officeHours.scheduleSession()}
                      className="rounded-lg bg-app-accent px-3 py-2 text-sm font-medium text-white disabled:opacity-60"
                    >
                      Schedule now
                    </button>
                  )}
                  {officeHours.canJoin && officeHours.activeSession && (
                    <button
                      type="button"
                      disabled={officeHours.isBusy}
                      onClick={() => void officeHours.joinWaitlist()}
                      className="rounded-lg border border-app-border px-3 py-2 text-sm text-app-text hover:bg-app-elevated disabled:opacity-60"
                    >
                      Join waitlist
                    </button>
                  )}
                  {officeHours.canManage && officeHours.activeSession && (
                    <>
                      <button
                        type="button"
                        disabled={officeHours.isBusy}
                        onClick={() => void officeHours.admitNext()}
                        className="rounded-lg bg-app-accent px-3 py-2 text-sm font-medium text-white disabled:opacity-60"
                      >
                        Admit next
                      </button>
                      <button
                        type="button"
                        disabled={officeHours.isBusy}
                        onClick={() => void officeHours.endSession()}
                        className="rounded-lg border border-red-500/50 px-3 py-2 text-sm text-red-200 hover:bg-red-500/10 disabled:opacity-60"
                      >
                        End session
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>

            {officeHours.activeSession && (
              <OfficeHoursVoiceSection key={officeHours.activeSession.id} session={officeHours.activeSession} />
            )}

            {officeHours.activeSession && officeHours.canManage && (
              <div className="rounded-xl border border-app-border bg-app-surface p-4">
                <h3 className="text-sm font-semibold text-app-text">Waitlist</h3>
                {officeHours.waitlist.length === 0 ? (
                  <p className="mt-2 text-sm text-app-muted">No learners waiting.</p>
                ) : (
                  <ul className="mt-3 flex flex-col gap-2">
                    {officeHours.waitlist.map((entry) => (
                      <li
                        key={`${entry.sessionId}:${entry.learnerUserId}:${entry.joinedAt}`}
                        className="flex items-center justify-between rounded-lg border border-app-border px-3 py-2 text-sm"
                      >
                        <span className="text-app-text">
                          Learner {entry.learnerUserId.slice(0, 8)}…
                        </span>
                        <span className="text-xs text-app-muted">{entry.status}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}

            {officeHours.sessions.length > 0 && (
              <div className="rounded-xl border border-app-border bg-app-surface p-4">
                <h3 className="text-sm font-semibold text-app-text">Recent sessions</h3>
                <ul className="mt-3 flex flex-col gap-2">
                  {officeHours.sessions.slice(0, 5).map((session) => (
                    <li
                      key={session.id}
                      className="flex items-center justify-between rounded-lg border border-app-border px-3 py-2 text-sm"
                    >
                      <span className="text-app-text">{session.status}</span>
                      <span className="text-xs text-app-muted">
                        {formatTimestamp(session.startsAt)} – {formatTimestamp(session.endsAt)}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  )
}

function OfficeHoursVoiceSection({ session }: { session: OfficeHoursSession }) {
  const voice = useOfficeHoursVoice(session.id, session.voiceChannelId)
  const isConnected = voice.status === 'connected'

  return (
    <div className="rounded-xl border border-app-border bg-app-surface p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-app-text">Voice room</h3>
          <p className="mt-1 text-sm text-app-muted">
            Office Hours reuses the study server voice channel for live audio.
          </p>
        </div>
        <span className="rounded-full border border-app-border px-2 py-0.5 text-xs text-app-muted">
          {voice.status === 'connected' ? 'In voice' : voice.status}
        </span>
      </div>

      {voice.error ? (
        <p
          role="status"
          aria-live="polite"
          className="mt-3 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200"
        >
          {voice.error}
        </p>
      ) : null}

      <div className="mt-4 flex flex-wrap gap-2">
        {!isConnected ? (
          <button
            type="button"
            disabled={voice.isBusy || voice.status === 'connecting'}
            onClick={() => void voice.joinVoice()}
            className="rounded-lg bg-app-accent px-3 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {voice.status === 'connecting' ? 'Connecting…' : 'Join voice'}
          </button>
        ) : (
          <>
            <button
              type="button"
              disabled={voice.isBusy}
              onClick={() => void voice.toggleMute()}
              className="rounded-lg border border-app-border px-3 py-2 text-sm text-app-text hover:bg-app-elevated disabled:opacity-60"
            >
              {voice.isMuted ? 'Unmute' : 'Mute'}
            </button>
            <button
              type="button"
              disabled={voice.isBusy}
              onClick={() => void voice.leaveVoice()}
              className="rounded-lg border border-red-500/50 px-3 py-2 text-sm text-red-200 hover:bg-red-500/10 disabled:opacity-60"
            >
              Leave voice
            </button>
          </>
        )}
      </div>
    </div>
  )
}
