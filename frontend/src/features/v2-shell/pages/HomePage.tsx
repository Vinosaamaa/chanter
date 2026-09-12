import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowUpRight, BookOpen, CalendarDays, Plus, RefreshCw } from 'lucide-react'

import { fetchHomeSummary, homeSummaryQueryKey } from '../../home/home-summary-api'
import { formatUserFacingApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'
import { buildHomeViewModel } from '../home/build-home-view-model'
import {
  HomeAttentionRow,
  HomeCourseCardView,
  HomeUpNextPanel,
} from '../components/HomeSections'
import { HomeStudyServerInvites } from '../components/HomeStudyServerInvites'

export function HomePage() {
  const user = useAuthStore((state) => state.user)
  const displayName =
    user?.displayName?.split(' ')[0] ?? user?.email?.split('@')[0] ?? 'there'

  const summaryQuery = useQuery({
    queryKey: homeSummaryQueryKey(user?.id),
    queryFn: fetchHomeSummary,
    enabled: Boolean(user?.id),
  })

  const model = useMemo(
    () => buildHomeViewModel(displayName, summaryQuery.data),
    [displayName, summaryQuery.data],
  )

  return (
    <div className="dashboard">
      <div className="dashboard-inner">
        <div className="greeting">
          <p className="greeting-date">{model.dateLabel}</p>
          <h1>{model.greeting}</h1>
          <p>Your Courses and conversations, in one place.</p>
        </div>

        {summaryQuery.isError ? (
          <div className="page-error" role="alert">
            <p>{formatUserFacingApiError(summaryQuery.error, 'Unable to load your Home.')}</p>
            <button type="button" onClick={() => void summaryQuery.refetch()}><RefreshCw size={16} />Try again</button>
          </div>
        ) : null}

        <HomeStudyServerInvites />
        {model.upNext[0]?.href ? <Link to={model.upNext[0].href} className="home-next-action"><CalendarDays aria-hidden="true" /><span><small>Up next</small><strong>{model.upNext[0].title}</strong><span>{model.upNext[0].detail}</span></span><ArrowUpRight aria-hidden="true" /></Link> : null}

        <div className="lower-grid">
          <section className="learning">
            <div className="section-heading"><h2>Continue learning</h2><Link to="/app/onboarding/join-or-create" className="quiet-link"><Plus size={16} />Join a Course</Link></div>
            {summaryQuery.isLoading ? (
              <div className="course-loading" role="status"><span>Loading your courses…</span><div /><div /></div>
            ) : summaryQuery.isError ? null : model.courses.length === 0 ? (
              <div className="learning-empty">
                <BookOpen size={36} aria-hidden="true" />
                <h3>A place for your next Course</h3>
                <p>Join your learning community with an invite, or create a Study Server of your own.</p>
                <Link to="/app/onboarding/join-or-create" className="desk-primary-button">Join or create a Study Server</Link>
              </div>
            ) : (
              <div className="course-grid">
                {model.courses.map((course) => (
                  <HomeCourseCardView key={course.id} course={course} />
                ))}
              </div>
            )}
            <HomeAttentionRow items={model.attention} />
          </section>

          <HomeUpNextPanel items={model.upNext} loading={summaryQuery.isLoading} unavailable={summaryQuery.isError} />
        </div>
      </div>
    </div>
  )
}
