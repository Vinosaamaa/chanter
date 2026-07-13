import { useState } from 'react'
import { CalendarDays, Check, ChevronLeft, ChevronRight, UsersRound } from 'lucide-react'

type CalendarFilter = 'All' | 'Office hours' | 'Events' | 'Deadlines' | 'Going'

const calendarDays = [
  28, 29, 30, 1, 2, 3, 4,
  5, 6, 7, 8, 9, 10, 11,
  12, 13, 14, 15, 16, 17, 18,
  19, 20, 21, 22, 23, 24, 25,
  26, 27, 28, 29, 30, 31, 1,
  2, 3, 4, 5, 6, 7, 8,
]

const eventDots: Record<number, string> = { 13: 'deadline', 16: 'office', 18: 'event', 22: 'event', 24: 'office', 28: 'going' }

export function CalendarPage() {
  const [filter, setFilter] = useState<CalendarFilter>('All')
  const [selectedDay, setSelectedDay] = useState(18)
  const [monthOffset, setMonthOffset] = useState(0)
  const monthLabel = monthOffset === 0 ? 'July 2026' : monthOffset < 0 ? 'June 2026' : 'August 2026'

  return (
    <section className="v2-workspace-page calendar-page">
      <div className="calendar-main">
        <header className="calendar-toolbar">
          <div className="month-switcher">
            <button type="button" aria-label="Previous month" onClick={() => setMonthOffset(-1)}><ChevronLeft /></button>
            <h1>{monthLabel}</h1>
            <button type="button" aria-label="Next month" onClick={() => setMonthOffset(1)}><ChevronRight /></button>
          </div>
          <button type="button" className="v2-outline-button" onClick={() => { setMonthOffset(0); setSelectedDay(11) }}>Today</button>
        </header>

        <div className="v2-chip-row calendar-filters" aria-label="Calendar filters">
          {(['All', 'Office hours', 'Events', 'Deadlines', 'Going'] as CalendarFilter[]).map((item) => (
            <button type="button" key={item} className={filter === item ? 'active' : undefined} onClick={() => setFilter(item)}><i className={`filter-dot ${item.toLowerCase().replace(' ', '-')}`} />{item}</button>
          ))}
        </div>

        <div className="calendar-weekdays" aria-hidden="true">
          {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => <span key={day}>{day}</span>)}
        </div>
        <div className="calendar-month-grid" role="grid" aria-label={monthLabel}>
          {calendarDays.map((day, index) => {
            const muted = index < 3 || index > 33
            const dot = monthOffset === 0 ? eventDots[day] : undefined
            return (
              <button type="button" role="gridcell" key={`${day}-${index}`} className={`${muted ? 'muted' : ''} ${selectedDay === day && !muted ? 'selected' : ''}`} onClick={() => !muted && setSelectedDay(day)}>
                <span>{day}</span>{dot ? <i className={dot} /> : null}
              </button>
            )
          })}
        </div>
      </div>

      <aside className="calendar-agenda">
        <h2>Friday Jul {selectedDay} <small>selected day</small></h2>
        <article className="agenda-card">
          <span className="agenda-icon blue"><CalendarDays /></span>
          <div><time>2:00 PM</time><p><strong>Office hours</strong> — CS 101</p><button type="button">Join</button></div>
        </article>
        <article className="agenda-card">
          <span className="agenda-icon purple"><UsersRound /></span>
          <div><time>6:00 PM</time><p><strong>Hackathon kickoff</strong> — Spring Bootcamp Hub</p><button type="button" className="going">Going <Check size={16} /></button></div>
        </article>

        <h2 className="upcoming-heading">Upcoming this week</h2>
        <div className="upcoming-list">
          <UpcomingRow date="Jul 22" day="Wed" title="Guest talk: AI in industry" context="All courses" tone="purple" />
          <UpcomingRow date="Jul 24" day="Fri" title="Midterm review — CS 101" context="CS 101" tone="blue" />
          <UpcomingRow date="Jul 28" day="Tue" title="Study group — MATH 201" context="MATH 201" tone="green" />
          <UpcomingRow date="Jul 13" day="Mon" title="Problem Set 3 due — CS 101" context="CS 101" tone="amber" badge="Deadline" />
        </div>
      </aside>
    </section>
  )
}

function UpcomingRow({ date, day, title, context, tone, badge }: { date: string; day: string; title: string; context: string; tone: string; badge?: string }) {
  return (
    <article className="upcoming-row">
      <span className={`agenda-icon ${tone}`}><CalendarDays /></span>
      <time><strong>{date}</strong><small>{day}</small></time>
      <p><strong>{title}</strong><small>{context}</small></p>
      {badge ? <b>{badge}</b> : null}
    </article>
  )
}
