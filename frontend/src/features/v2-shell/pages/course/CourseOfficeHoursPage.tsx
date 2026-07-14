import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  ArrowLeft,
  CalendarDays,
  Hand,
  Headphones,
  Mic,
  MicOff,
  Phone,
  Play,
  Plus,
  Trash2,
} from 'lucide-react'

import type { PublicUserProfile } from '../../../friends/types'
import { useOfficeHoursPanel } from '../../../support-operations/hooks/use-office-hours-panel'
import type { OfficeHoursParticipant, OfficeHoursSession } from '../../../support-operations/support-operations-types'
import { useOfficeHoursVoice } from '../../../voice/hooks/use-office-hours-voice'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  month: 'short',
  day: 'numeric',
})
const timeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: 'numeric',
  minute: '2-digit',
})

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Choose a start time' : dateFormatter.format(date)
}

function formatTimeRange(session: OfficeHoursSession): string {
  return `${timeFormatter.format(new Date(session.startsAt))} - ${timeFormatter.format(new Date(session.endsAt))}`
}

function toLocalInputValue(value: Date): string {
  const offset = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offset).toISOString().slice(0, 16)
}

function defaultStartsAt(): Date {
  const next = new Date(Date.now() + 60 * 60 * 1000)
  next.setMinutes(0, 0, 0)
  return next
}

export function CourseOfficeHoursPage() {
  const { courseCapabilities, selectedCohort: cohort } = useV2CourseWorkspace()
  const officeHours = useOfficeHoursPanel(cohort?.id)
  const voice = useOfficeHoursVoice(officeHours.liveSession?.id ?? null)
  const ownerMode = courseCapabilities.canScheduleOfficeHours && officeHours.canManage
  const [editingSession, setEditingSession] = useState<OfficeHoursSession | null | undefined>(undefined)

  if (!cohort) {
    return <div className="office-hours-page"><p className="office-empty-state">Select a cohort to view Office Hours.</p></div>
  }

  if (editingSession !== undefined && ownerMode) {
    return (
      <ScheduleView
        session={editingSession}
        officeHours={officeHours}
        onBack={() => setEditingSession(undefined)}
      />
    )
  }

  return (
    <div className={`office-hours-page ${ownerMode ? 'owner' : 'learner'}`}>
      {officeHours.isLoading ? <p className="office-empty-state">Loading Office Hours...</p> : null}
      {!officeHours.isLoading && ownerMode ? (
        <>
          {officeHours.liveSession ? (
            <OwnerLivePanel officeHours={officeHours} voice={voice} cohortName={cohort.name} />
          ) : (
            <OwnerNextPanel officeHours={officeHours} />
          )}
          <UpcomingPanel
            sessions={officeHours.upcomingSessions}
            isBusy={officeHours.isBusy}
            onSchedule={() => setEditingSession(null)}
            onEdit={setEditingSession}
            onStart={(sessionId) => void officeHours.startSession(sessionId)}
            onCancel={(session) => {
              if (window.confirm(`Cancel Office Hours on ${formatDate(session.startsAt)}?`)) {
                void officeHours.cancelSession(session.id)
              }
            }}
          />
        </>
      ) : null}
      {!officeHours.isLoading && !ownerMode ? (
        <LearnerPanel officeHours={officeHours} voice={voice} cohortName={cohort.name} />
      ) : null}
      {officeHours.accessDenied ? (
        <p className="inline-error office-hours-error">You do not have access to Office Hours for this cohort.</p>
      ) : null}
      {officeHours.error ? <p className="inline-error office-hours-error">{officeHours.error}</p> : null}
      {officeHours.actionMessage ? <p className="inline-success office-hours-error">{officeHours.actionMessage}</p> : null}
    </div>
  )
}

type OfficeHook = ReturnType<typeof useOfficeHoursPanel>
type VoiceHook = ReturnType<typeof useOfficeHoursVoice>

function profileName(userId: string, profilesById: Record<string, PublicUserProfile>): string {
  return profilesById[userId]?.displayName ?? `Member ${userId.slice(0, 8)}`
}

function toneFor(userId: string): 'blue' | 'amber' | 'purple' | 'green' {
  const tones = ['blue', 'amber', 'purple', 'green'] as const
  let hash = 0
  for (const character of userId) hash = (hash * 31 + character.charCodeAt(0)) >>> 0
  return tones[hash % tones.length]
}

function OfficeHeading({ cohortName, session }: { cohortName: string; session: OfficeHoursSession }) {
  return (
    <header className="office-heading">
      <span><Headphones /></span>
      <div>
        <h2>Office Hours <b>LIVE</b></h2>
        <p>{cohortName} - {formatDate(session.startsAt)}, {formatTimeRange(session)}</p>
        <small>Open voice session - join to listen, raise hand to speak</small>
      </div>
    </header>
  )
}

function Person({
  participant,
  profilesById,
  host,
  action,
}: {
  participant: OfficeHoursParticipant
  profilesById: Record<string, PublicUserProfile>
  host?: boolean
  action?: ReactNode
}) {
  const name = profileName(participant.userId, profilesById)
  return (
    <article className={participant.canSpeak ? 'speaking' : undefined}>
      <V2Avatar name={name} tone={toneFor(participant.userId)} size="lg" online={participant.canSpeak} />
      <strong>{name}</strong>
      {host ? <b>HOST</b> : null}
      {participant.handRaised ? <span className="hand-icon" title="Hand raised"><Hand /></span> : null}
      {action}
    </article>
  )
}

function OwnerLivePanel({
  officeHours,
  voice,
  cohortName,
}: {
  officeHours: OfficeHook
  voice: VoiceHook
  cohortName: string
}) {
  const session = officeHours.liveSession!
  const speakers = officeHours.participants.filter((participant) => participant.canSpeak)
  const listeners = officeHours.participants.filter((participant) => !participant.canSpeak)
  const raised = officeHours.participants.filter((participant) => participant.handRaised)
  const host = speakers.find((participant) => participant.userId === session.scheduledByUserId)
  const guestsWithSpeakingAccess = speakers.filter((participant) => participant.userId !== session.scheduledByUserId)
  const currentParticipant = officeHours.currentParticipant
  const voiceStatus = voice.status
  const voiceCanSpeak = voice.canSpeak
  const refreshVoicePermissions = voice.refreshPermissions

  useEffect(() => {
    if (
      voiceStatus === 'connected'
      && currentParticipant
      && voiceCanSpeak !== currentParticipant.canSpeak
    ) {
      void refreshVoicePermissions()
    }
  }, [currentParticipant, refreshVoicePermissions, voiceCanSpeak, voiceStatus])

  const joinHostAudio = async () => {
    const participant = await officeHours.joinSession()
    if (participant) await voice.joinVoice()
  }

  const disconnectHostAudio = async () => {
    await voice.leaveVoice()
    await officeHours.leaveSession()
  }

  const endSession = async () => {
    if (!window.confirm('End this live Office Hours session for everyone?')) return
    await voice.leaveVoice()
    await officeHours.endSession()
  }

  return (
    <section className="owner-live-office">
      <OfficeHeading cohortName={cohortName} session={session} />
      <div className="owner-host">
        <h3>HOST</h3>
        {host ? (
          <Person participant={host} profilesById={officeHours.profilesById} host />
        ) : (
          <p className="office-section-empty">Host has not connected audio yet.</p>
        )}
        <div className="owner-audio-controls">
          {voice.status === 'connected' ? (
            <>
              <button type="button" disabled={voice.isBusy} onClick={() => void voice.toggleMute()}>
                {voice.isMuted ? <MicOff /> : <Mic />} {voice.isMuted ? 'Unmute' : 'Mute'}
              </button>
              <button type="button" disabled={voice.isBusy || officeHours.isBusy} onClick={() => void disconnectHostAudio()}>
                <Phone /> Disconnect audio
              </button>
            </>
          ) : (
            <button type="button" disabled={voice.isBusy || officeHours.isBusy} onClick={() => void joinHostAudio()}>
              <Headphones /> Join host audio
            </button>
          )}
        </div>
      </div>
      <div className="owner-speakers">
        <h3>SPEAKING ({guestsWithSpeakingAccess.length})</h3>
        {guestsWithSpeakingAccess.map((participant) => (
          <Person
            key={participant.userId}
            participant={participant}
            profilesById={officeHours.profilesById}
            action={(
              <button
                type="button"
                className="grant-speaking-button"
                disabled={officeHours.isBusy}
                onClick={() => void officeHours.grantSpeaking(participant.userId, false)}
              >
                <MicOff /> Return to listener
              </button>
            )}
          />
        ))}
        {guestsWithSpeakingAccess.length === 0 ? <p className="office-section-empty">No learner is speaking.</p> : null}
      </div>
      <div className="owner-listeners">
        <h3>LISTENING ({listeners.length})</h3>
        <div>
          {listeners.slice(0, 8).map((participant) => {
            const name = profileName(participant.userId, officeHours.profilesById)
            return <V2Avatar key={participant.userId} name={name} tone={toneFor(participant.userId)} size="md" />
          })}
          {listeners.length > 8 ? <span>+{listeners.length - 8}</span> : null}
          {listeners.length === 0 ? <p className="office-section-empty">No listeners yet.</p> : null}
        </div>
      </div>
      <div className="owner-hands">
        <h3>HANDS RAISED ({raised.length})</h3>
        {raised.map((participant) => (
          <Person
            key={participant.userId}
            participant={participant}
            profilesById={officeHours.profilesById}
            action={(
              <button
                type="button"
                className="grant-speaking-button"
                disabled={officeHours.isBusy}
                aria-label={`Grant speaking access to ${profileName(participant.userId, officeHours.profilesById)}`}
                onClick={() => void officeHours.grantSpeaking(participant.userId, true)}
              >
                <Mic /> Allow to speak
              </button>
            )}
          />
        ))}
        {raised.length === 0 ? <p className="office-section-empty">No hands raised.</p> : null}
      </div>
      <button type="button" className="end-session-button" disabled={officeHours.isBusy} onClick={() => void endSession()}>
        <Phone /> End session
      </button>
    </section>
  )
}

function OwnerNextPanel({ officeHours }: { officeHours: OfficeHook }) {
  const next = officeHours.upcomingSessions[0]
  return (
    <section className="next-office-summary">
      <h2>Next office hours</h2>
      {next ? (
        <>
          <article>
            <span className="office-calendar-icon"><CalendarDays /></span>
            <div><strong>{formatDate(next.startsAt)}</strong><p>{formatTimeRange(next)}</p></div>
          </article>
          <p>Scheduled for this cohort.</p>
          <button type="button" disabled={officeHours.isBusy} onClick={() => void officeHours.startSession(next.id)}>
            <Play /> Start session
          </button>
        </>
      ) : <p className="office-section-empty">No Office Hours are scheduled.</p>}
    </section>
  )
}

function UpcomingPanel({
  sessions,
  isBusy,
  onSchedule,
  onEdit,
  onStart,
  onCancel,
}: {
  sessions: OfficeHoursSession[]
  isBusy: boolean
  onSchedule: () => void
  onEdit: (session: OfficeHoursSession) => void
  onStart: (sessionId: string) => void
  onCancel: (session: OfficeHoursSession) => void
}) {
  return (
    <section className="upcoming-office-panel">
      <header><h2>Upcoming office hours</h2><button type="button" className="v2-outline-button" onClick={onSchedule}><Plus /> Schedule</button></header>
      {sessions.map((session) => (
        <article key={session.id}>
          <span className="office-calendar-icon"><CalendarDays /></span>
          <div><strong>{formatDate(session.startsAt)}</strong><p>{formatTimeRange(session)}</p></div>
          <button type="button" disabled={isBusy} onClick={() => onStart(session.id)}><Play /> Start</button>
          <button type="button" disabled={isBusy} onClick={() => onEdit(session)}>Edit</button>
          <button type="button" disabled={isBusy} className="danger-link" onClick={() => onCancel(session)}>Cancel session</button>
        </article>
      ))}
      {sessions.length === 0 ? <p className="office-section-empty">No upcoming sessions.</p> : null}
    </section>
  )
}

function LearnerPanel({
  officeHours,
  voice,
  cohortName,
}: {
  officeHours: OfficeHook
  voice: VoiceHook
  cohortName: string
}) {
  const session = officeHours.liveSession
  const joined = officeHours.currentParticipant !== null
  const participant = officeHours.currentParticipant
  const speakers = officeHours.participants.filter((item) => item.canSpeak)
  const listeners = officeHours.participants.filter((item) => !item.canSpeak)
  const raised = officeHours.participants.filter((item) => item.handRaised)
  const voiceStatus = voice.status
  const voiceCanSpeak = voice.canSpeak
  const refreshVoicePermissions = voice.refreshPermissions

  useEffect(() => {
    if (
      voiceStatus === 'connected'
      && participant
      && voiceCanSpeak !== participant.canSpeak
    ) {
      void refreshVoicePermissions()
    }
  }, [participant, refreshVoicePermissions, voiceCanSpeak, voiceStatus])

  if (!session) {
    return (
      <section className="learner-office-panel">
        <div className="office-empty-live">
          <Headphones />
          <h2>No live Office Hours</h2>
          {officeHours.upcomingSessions[0] ? (
            <p>Next: {formatDate(officeHours.upcomingSessions[0].startsAt)}, {formatTimeRange(officeHours.upcomingSessions[0])}</p>
          ) : <p>No session is scheduled for {cohortName}.</p>}
        </div>
      </section>
    )
  }

  const join = async () => {
    const joinedParticipant = await officeHours.joinSession()
    if (joinedParticipant) await voice.joinVoice()
  }
  const leave = async () => {
    await voice.leaveVoice()
    await officeHours.leaveSession()
  }

  return (
    <section className="learner-office-panel">
      <OfficeHeading cohortName={cohortName} session={session} />
      <div className="office-speakers">
        <h3>SPEAKING NOW</h3>
        {speakers.map((item) => (
          <Person
            key={item.userId}
            participant={item}
            profilesById={officeHours.profilesById}
            host={item.userId === session.scheduledByUserId}
          />
        ))}
        {speakers.length === 0 ? <p className="office-section-empty">Waiting for the host.</p> : null}
      </div>
      <div className="office-listeners">
        <h3>LISTENING ({listeners.length})</h3>
        <div>
          {listeners.slice(0, 8).map((item) => {
            const name = profileName(item.userId, officeHours.profilesById)
            return <span key={item.userId}><V2Avatar name={name} tone={toneFor(item.userId)} size="lg" /><small>{name}</small></span>
          })}
          {listeners.length > 8 ? <span className="more-listeners"><i>+{listeners.length - 8}</i><small>more</small></span> : null}
        </div>
      </div>
      <div className="office-raised">
        <h3>HANDS RAISED ({raised.length})</h3>
        {raised.map((item) => <Person key={item.userId} participant={item} profilesById={officeHours.profilesById} />)}
        <p>{participant?.canSpeak ? 'You can speak now' : 'Host will call on you to speak'}</p>
      </div>
      {voice.error ? <p className="inline-error">{voice.error}</p> : null}
      {!joined ? (
        <button type="button" className="join-office-button" disabled={officeHours.isBusy || voice.isBusy} onClick={() => void join()}>
          <Headphones /> Join as listener
        </button>
      ) : (
        <div className="office-control-bar">
          <span><i />{voice.status === 'connected' ? 'Connected' : 'Joined'} - {participant?.canSpeak ? 'Speaking' : 'Listening'}</span>
          {voice.status !== 'connected' ? (
            <button type="button" className="mute-control" onClick={() => void voice.joinVoice()} aria-label="Connect audio"><Headphones /></button>
          ) : (
            <button
              type="button"
              className="mute-control"
              disabled={!participant?.canSpeak || voice.isBusy}
              title={participant?.canSpeak ? 'Mute or unmute microphone' : 'The host must grant speaking access first'}
              aria-label={voice.isMuted ? 'Unmute microphone' : 'Mute microphone'}
              onClick={() => void voice.toggleMute()}
            >
              {voice.isMuted ? <MicOff /> : <Mic />}
            </button>
          )}
          <button
            type="button"
            className={participant?.handRaised ? 'hand active' : 'hand'}
            disabled={officeHours.isBusy || participant?.canSpeak}
            onClick={() => void officeHours.setHandRaised(!participant?.handRaised)}
          >
            <Hand /> {participant?.handRaised ? 'Lower Hand' : 'Raise Hand'}
          </button>
          <button type="button" className="leave" disabled={officeHours.isBusy || voice.isBusy} onClick={() => void leave()}><Phone /> Leave</button>
        </div>
      )}
    </section>
  )
}

function ScheduleView({
  session,
  officeHours,
  onBack,
}: {
  session: OfficeHoursSession | null
  officeHours: OfficeHook
  onBack: () => void
}) {
  const initialStart = useMemo(() => session ? new Date(session.startsAt) : defaultStartsAt(), [session])
  const initialDuration = useMemo(() => session
    ? Math.max(15, Math.round((new Date(session.endsAt).getTime() - new Date(session.startsAt).getTime()) / 60_000))
    : 60, [session])
  const [startsAt, setStartsAt] = useState(toLocalInputValue(initialStart))
  const [durationMinutes, setDurationMinutes] = useState(initialDuration)
  const [formError, setFormError] = useState<string | null>(null)
  const startDate = new Date(startsAt)
  const endsAt = new Date(startDate.getTime() + durationMinutes * 60_000)
  const validPreview = !Number.isNaN(startDate.getTime()) && !Number.isNaN(endsAt.getTime())

  const save = async () => {
    const start = startDate
    if (Number.isNaN(start.getTime()) || durationMinutes < 15) {
      setFormError('Choose a valid start time and a duration of at least 15 minutes.')
      return
    }
    const input = { startsAt: start.toISOString(), endsAt: endsAt.toISOString() }
    const saved = session
      ? await officeHours.updateSession(session.id, input)
      : await officeHours.scheduleSession(input)
    if (saved) onBack()
  }

  return (
    <div className="office-schedule-layout">
      <section className="next-office-summary">
        <h2>{session ? 'Editing office hours' : 'New office hours'}</h2>
        <article>
          <span className="office-calendar-icon"><CalendarDays /></span>
          <div>
            <strong>{validPreview ? formatDate(startDate.toISOString()) : 'Choose a start time'}</strong>
            <p>{validPreview ? `${timeFormatter.format(startDate)} - ${timeFormatter.format(endsAt)} (${durationMinutes} min)` : 'Enter a valid date and duration'}</p>
          </div>
        </article>
        <p>This single session is saved for the selected cohort.</p>
        <button type="button" disabled={!session || officeHours.isBusy} onClick={() => session && void officeHours.startSession(session.id)}><Play /> Start session</button>
      </section>
      <section className="office-schedule-form">
        <button type="button" className="back-link" onClick={onBack}><ArrowLeft /> Back to upcoming</button>
        <h2>{session ? 'Edit office hours' : 'Schedule office hours'}</h2>
        <label>Start date and time<div><input aria-label="Start date and time" type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /><CalendarDays /></div></label>
        <div className="office-form-row">
          <label>Duration<div><input aria-label="Duration in minutes" type="number" min="15" step="15" value={durationMinutes} onChange={(event) => setDurationMinutes(Number(event.target.value))} /><span>min</span></div></label>
          <label>Ends<div><input aria-label="End time" value={Number.isNaN(endsAt.getTime()) ? '' : timeFormatter.format(endsAt)} readOnly /></div></label>
        </div>
        {formError ? <p className="inline-error">{formError}</p> : null}
        <footer><button type="button" className="v2-outline-button" onClick={onBack}>Cancel</button><button type="button" className="v2-primary-button" disabled={officeHours.isBusy} onClick={() => void save()}>Save</button></footer>
        {session ? (
          <button
            type="button"
            className="delete-session"
            disabled={officeHours.isBusy}
            onClick={() => {
              if (window.confirm(`Cancel Office Hours on ${formatDate(session.startsAt)}?`)) {
                void officeHours.cancelSession(session.id).then((cancelled) => { if (cancelled) onBack() })
              }
            }}
          ><Trash2 /> Cancel session</button>
        ) : null}
      </section>
    </div>
  )
}
