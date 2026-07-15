import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'

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
    user?.displayName?.split(' ')[0] ?? user?.email?.split('@')[0] ?? 'Sam'

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
          <h1>{model.greeting}</h1>
          <p>{model.dateLabel}</p>
        </div>

        {summaryQuery.isError ? (
          <p style={{ color: 'var(--muted)' }}>
            {formatUserFacingApiError(summaryQuery.error, 'Unable to load home summary.')}
          </p>
        ) : null}

        {summaryQuery.isLoading ? (
          <p style={{ color: 'var(--muted)' }}>Loading your home…</p>
        ) : (
          <HomeAttentionRow items={model.attention} />
        )}
        <HomeStudyServerInvites />

        <div className="lower-grid">
          <section className="learning">
            <h2>Continue learning</h2>
            {summaryQuery.isLoading ? (
              <p style={{ color: 'var(--muted)' }}>Loading your courses…</p>
            ) : model.courses.length === 0 ? (
              <div className="empty-search">
                No courses yet. Use <strong>Join or create</strong> in the sidebar.
              </div>
            ) : (
              <div className="course-grid">
                {model.courses.map((course) => (
                  <HomeCourseCardView key={course.id} course={course} />
                ))}
              </div>
            )}
          </section>

          <HomeUpNextPanel items={summaryQuery.isLoading ? [] : model.upNext} />
        </div>
      </div>
    </div>
  )
}
