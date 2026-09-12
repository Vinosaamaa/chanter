import { Link } from 'react-router-dom'
import {
  CalendarDays,
  ClipboardList,
  Megaphone,
  MessageSquare,
  UsersRound,
  ArrowUpRight,
} from 'lucide-react'
import type { CSSProperties } from 'react'

import type { HomeAttentionItem, HomeCourseCard, HomeUpNextItem } from '../home/build-home-view-model'

function AttentionIcon({ item }: { item: HomeAttentionItem }) {
  switch (item.icon) {
    case 'calendar':
      return <CalendarDays size={25} />
    case 'chat':
      return <MessageSquare size={25} />
    case 'megaphone':
      return <Megaphone size={26} />
  }
}

export function HomeAttentionRow({ items }: { items: HomeAttentionItem[] }) {
  if (items.length === 0) {
    return null
  }

  return (
    <section className="home-attention" aria-labelledby="home-attention-heading">
      <h2 id="home-attention-heading">Needs attention</h2>
      <div className="notice-row">
      {items.map((item) => (
        <article key={item.id} className={`notice ${item.kind}`}>
          <span className={`notice-icon ${item.tone}`}>
            <AttentionIcon item={item} />
          </span>
          <div>
            <p>
              <strong>{item.headline}</strong>
              {item.suffix ? (
                <span style={item.suffixOnNewLine ? { display: 'block' } : undefined}>{item.suffix}</span>
              ) : null}
            </p>
            {item.actionLabel && item.href ? (
              <Link to={item.href}>{item.actionLabel}</Link>
            ) : null}
          </div>
        </article>
      ))}
      </div>
    </section>
  )
}

export function HomeCourseCardView({ course }: { course: HomeCourseCard }) {
  const hasProgress = typeof course.progress === 'number'
  const style = {
    '--course-color': course.color,
    '--course-end': course.colorEnd,
    ...(hasProgress ? { '--course-progress': `${course.progress}%` } : {}),
  } as CSSProperties

  return (
    <Link to={course.href} className="course-card" style={style}>
      <div className="course-cover-context"><span>{course.cohortLabel}</span><ArrowUpRight size={20} aria-hidden="true" /></div>
      <div className="course-title-row">
        <div>
          <h3>
            {course.code !== course.title ? `${course.code} — ${course.title}` : course.title}
          </h3>
          <p>{course.professor}</p>
        </div>
      </div>

      {hasProgress ? (
        <div className="progress-row">
          <div className="progress-track" role="progressbar" aria-label="Course progress" aria-valuenow={course.progress ?? 0} aria-valuemin={0} aria-valuemax={100}>
            <span />
          </div>
          <small>{course.progress}% complete</small>
        </div>
      ) : (
        <div className="progress-row">
          <small>Progress unavailable</small>
        </div>
      )}
    </Link>
  )
}

function UpNextIcon({ item }: { item: HomeUpNextItem }) {
  switch (item.icon) {
    case 'users':
      return <UsersRound size={27} />
    case 'clipboard':
      return <ClipboardList size={27} />
    case 'calendar':
      return <CalendarDays size={27} />
  }
}

export function HomeUpNextPanel({ items, loading = false, unavailable = false }: { items: HomeUpNextItem[]; loading?: boolean; unavailable?: boolean }) {
  return (
    <aside className="up-next">
      <div className="section-heading"><h2>Up next</h2><CalendarDays size={20} aria-hidden="true" /></div>
      {loading ? <p className="schedule-empty" role="status">Loading your schedule…</p> : unavailable ? <p className="schedule-empty">Your schedule could not be loaded.</p> : items.length === 0 ? (
        <div className="schedule-empty"><CalendarDays size={28} aria-hidden="true" /><p>Nothing coming up yet.</p><span>Office Hours and community events will appear here.</span></div>
      ) : (
        <div className="timeline">
          {items.map((item) => (
            <div className="timeline-item" key={item.id}>
              <span className={`timeline-icon ${item.tone}`}>
                <UpNextIcon item={item} />
              </span>
              <div className="timeline-copy">
                <p>
                  <strong>{item.title}</strong>
                  {item.suffix ? <span>{item.suffix}</span> : null}
                </p>
                <p>{item.detail}</p>
                {item.actionLabel && item.href ? (
                  <Link to={item.href}>{item.actionLabel}</Link>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      )}
      <Link to="/app/calendar" className="calendar-link">View calendar<ArrowUpRight size={16} aria-hidden="true" /></Link>
    </aside>
  )
}
