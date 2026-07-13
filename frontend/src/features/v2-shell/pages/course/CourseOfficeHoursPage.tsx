import { useState } from 'react'
import { ArrowLeft, CalendarDays, ChevronDown, Hand, Headphones, MicOff, Phone, Plus, Trash2 } from 'lucide-react'

import { useOfficeHoursPanel } from '../../../support-operations/hooks/use-office-hours-panel'
import { useOfficeHoursVoice } from '../../../voice/hooks/use-office-hours-voice'
import { useVoiceChannel } from '../../../voice/hooks/use-voice-channel'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

const listeners = [
  ['Sam', 'blue'], ['Jordan', 'amber'], ['Priya', 'purple'], ['Noah', 'green'],
] as readonly (readonly [string, 'blue' | 'amber' | 'purple' | 'green'])[]

const ownerListeners = [...listeners, ['Alex', 'blue'], ['Maya', 'purple']] as readonly (readonly [string, 'blue' | 'amber' | 'purple' | 'green'])[]

export function CourseOfficeHoursPage() {
  const { course, courseCapabilities, selectedCohort: cohort } = useV2CourseWorkspace()
  const officeHours = useOfficeHoursPanel(cohort?.id)
  const voiceChannel = course.channels.find((channel) => channel.kind === 'VOICE')
  const officeVoice = useOfficeHoursVoice(officeHours.activeSession?.id ?? null, officeHours.activeSession?.voiceChannelId ?? voiceChannel?.id ?? null)
  const channelVoice = useVoiceChannel(voiceChannel?.id ?? 'voice-demo')
  const voice = officeHours.activeSession ? officeVoice : channelVoice
  const ownerMode = courseCapabilities.canScheduleOfficeHours && (officeHours.canSchedule || officeHours.canManage)
  const [showSchedule, setShowSchedule] = useState(false)
  const [handRaised, setHandRaised] = useState(false)
  const [repeatWeekly, setRepeatWeekly] = useState(true)

  const raiseHand = () => {
    setHandRaised((value) => !value)
    if (voice.status !== 'connected') void voice.joinVoice()
  }

  if (ownerMode && showSchedule) {
    return <OwnerScheduleView officeHours={officeHours} repeatWeekly={repeatWeekly} setRepeatWeekly={setRepeatWeekly} onBack={() => setShowSchedule(false)} />
  }

  return (
    <div className={`office-hours-page ${ownerMode ? 'owner' : 'learner'}`}>
      {ownerMode ? (
        <>
          <OwnerLivePanel officeHours={officeHours} voice={voice} cohortName={cohort?.name ?? 'Cohort'} />
          <section className="upcoming-office-panel">
            <header><h2>Upcoming office hours</h2><button type="button" className="v2-outline-button" onClick={() => setShowSchedule(true)}><Plus />Schedule</button></header>
            {[['Thu, May 16','2:00 – 3:00 PM'],['Thu, May 23','2:00 – 3:00 PM']].map(([date,time]) => <article key={date}><span className="office-calendar-icon"><CalendarDays /></span><div><strong>{date}</strong><p>{time} <b>Weekly</b></p></div><button type="button" onClick={() => setShowSchedule(true)}>Edit</button><button type="button" className="danger-link">Cancel session</button></article>)}
          </section>
        </>
      ) : (
        <section className="learner-office-panel">
          <OfficeHeading cohortName={cohort?.name ?? 'Cohort'} />
          <div className="office-speakers"><h3>SPEAKING NOW</h3><Person name="Dr. Alex Johnson" badge="HOST" tone="amber" speaking /><Person name="Maria Gonzalez" badge="TA" tone="purple" muted /></div>
          <div className="office-listeners"><h3>LISTENING (24)</h3><div>{listeners.map(([name,tone], index) => <span key={name}><V2Avatar name={name} tone={tone} size="lg" /><small>{name}</small>{index === 0 ? <b>Listening</b> : null}</span>)}<span className="more-listeners"><i>+20</i><small>more</small></span></div></div>
          <div className="office-raised"><h3>HANDS RAISED</h3><Person name="Daniel Anderson" tone="green" hand /><Person name="Maya Gonzalez" tone="purple" hand /><p>Host will call on you to speak</p></div>
          {voice.error && voiceChannel?.id ? <p className="inline-error">{voice.error}</p> : null}
          <div className="office-control-bar"><span><i />{voice.status === 'connected' ? 'Connected' : 'Ready'} · Listening</span><button type="button" className="mute-control" onClick={() => void voice.toggleMute()}><MicOff /></button><button type="button" className={handRaised ? 'hand active' : 'hand'} onClick={raiseHand}><Hand />{handRaised ? 'Hand Raised' : 'Raise Hand'}</button><button type="button" className="leave" onClick={() => void voice.leaveVoice()}><Phone />Leave</button></div>
        </section>
      )}
      {officeHours.error && cohort?.id ? <p className="inline-error office-hours-error">{officeHours.error}</p> : null}
      {officeHours.actionMessage ? <p className="inline-success office-hours-error">{officeHours.actionMessage}</p> : null}
    </div>
  )
}

type OfficeHook = ReturnType<typeof useOfficeHoursPanel>
type VoiceHook = ReturnType<typeof useOfficeHoursVoice> | ReturnType<typeof useVoiceChannel>

function OfficeHeading({ cohortName }: { cohortName: string }) {
  return <header className="office-heading"><span><Headphones /></span><div><h2>Office Hours <b>LIVE</b></h2><p>{cohortName} · Today 2:00 – 3:00 PM</p><small>Open voice session — join to listen, raise hand to speak</small></div></header>
}

function Person({ name, badge, tone, speaking, muted, hand }: { name: string; badge?: string; tone: 'amber' | 'purple' | 'green'; speaking?: boolean; muted?: boolean; hand?: boolean }) {
  return <article className={speaking ? 'speaking' : undefined}><V2Avatar name={name} tone={tone} size="lg" online={speaking} /><strong>{name}</strong>{badge ? <b className={badge === 'TA' ? 'ta' : ''}>{badge}</b> : null}{muted ? <MicOff /> : null}{hand ? <span className="hand-icon"><Hand /></span> : null}</article>
}

function OwnerLivePanel({ officeHours, voice, cohortName }: { officeHours: OfficeHook; voice: VoiceHook; cohortName: string }) {
  return <section className="owner-live-office"><OfficeHeading cohortName={cohortName} /><div className="owner-host"><h3>HOST</h3><article><V2Avatar name="Dr. Alex Johnson" tone="amber" size="lg" online /><strong>Dr. Alex Johnson</strong><span className="audio-wave">▂▅▇▄▆▃▇▅▂▆▃▇</span></article></div><div className="owner-listeners"><h3>LISTENING (12)</h3><div>{ownerListeners.map(([name,tone]) => <V2Avatar key={name} name={name} tone={tone} size="md" />)}<span>+4</span></div></div><div className="owner-hands"><h3>HANDS RAISED (2)</h3><Person name="Daniel Anderson" tone="green" hand /><Person name="Maya Gonzalez" tone="purple" hand /></div><button type="button" className="end-session-button" onClick={() => { void voice.leaveVoice(); void officeHours.endSession() }}><Phone />End session</button></section>
}

function OwnerScheduleView({ officeHours, repeatWeekly, setRepeatWeekly, onBack }: { officeHours: OfficeHook; repeatWeekly: boolean; setRepeatWeekly: (value: boolean) => void; onBack: () => void }) {
  const save = () => { void officeHours.scheduleSession(); onBack() }
  return <div className="office-schedule-layout"><section className="next-office-summary"><h2>Next office hours</h2><article><span className="office-calendar-icon"><CalendarDays /></span><div><strong>Thu, May 16</strong><p>2:00 – 3:00 PM (60 min)</p><b>Weekly</b></div></article><p>Opens for learners at 2:00 PM · 4 days away</p><hr /><p>Last session: May 9 — 14 joined, 45 min</p><button type="button" disabled>Start session early</button></section><section className="office-schedule-form"><button type="button" className="back-link" onClick={onBack}><ArrowLeft />Back to upcoming</button><h2>Schedule office hours</h2><label>Date<div><input value="May 16 2026" readOnly /><CalendarDays /></div></label><div className="office-form-row"><label>Time<div><input value="2:00 PM" readOnly /><ChevronDown /></div></label><label>Duration<div><input value="60 minutes" readOnly /><ChevronDown /></div></label></div><div className="repeat-row"><div><strong>Repeat weekly</strong><small>repeats every Thursday</small></div><button type="button" role="switch" aria-checked={repeatWeekly} className={repeatWeekly ? 'active' : ''} onClick={() => setRepeatWeekly(!repeatWeekly)}><i /></button></div><footer><button type="button" className="v2-outline-button" onClick={onBack}>Cancel</button><button type="button" className="v2-primary-button" onClick={save} disabled={officeHours.isBusy}>Save</button></footer><button type="button" className="delete-session"><Trash2 />Delete session</button></section></div>
}
