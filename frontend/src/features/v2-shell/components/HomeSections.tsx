import { Link } from 'react-router-dom'
import {
  CalendarDays,
  ClipboardList,
  Megaphone,
  MessageSquare,
  UsersRound,
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
      <div className="course-title-row">
        <span className="course-dot" />
        <div>
          <h3>
            {course.code} <span>— {course.title}</span>
          </h3>
          <p>
            {course.cohortLabel} <span>·</span> {course.professor}
          </p>
        </div>
      </div>

      {hasProgress ? (
        <div className="progress-row">
          <div className="progress-track" aria-label={`${course.progress}% complete`}>
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

export function HomeUpNextPanel({ items }: { items: HomeUpNextItem[] }) {
  return (
    <aside className="up-next">
      <h2>Up next</h2>
      {items.length === 0 ? (
        <p className="empty-search" style={{ marginTop: '0.75rem' }}>
          Nothing coming up yet.
        </p>
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
                ) : item.actionLabel ? (
                  <button type="button" disabled>
                    {item.actionLabel}
                  </button>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      )}
    </aside>
  )
}
