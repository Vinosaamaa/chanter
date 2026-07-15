import { useMemo } from 'react'

import { useAuthStore } from '../../../stores/auth-store'
import { useV2SidebarData } from '../hooks/use-v2-sidebar-data'
import { buildHomeViewModel } from '../home/build-home-view-model'
import {
  HomeAttentionRow,
  HomeCourseCardView,
  HomeUpNextPanel,
} from '../components/HomeSections'
import { HomeStudyServerInvites } from '../components/HomeStudyServerInvites'

export function HomePage() {
  const user = useAuthStore((state) => state.user)
  const sidebar = useV2SidebarData()
  const displayName =
    user?.displayName?.split(' ')[0] ?? user?.email?.split('@')[0] ?? 'Sam'

  const model = useMemo(
    () => buildHomeViewModel(displayName, sidebar.allCourses),
    [displayName, sidebar.allCourses],
  )

  return (
    <div className="dashboard">
      <div className="dashboard-inner">
        <div className="greeting">
          <h1>{model.greeting}</h1>
          <p>{model.dateLabel}</p>
        </div>

        <HomeAttentionRow items={model.attention} />
        <HomeStudyServerInvites />

        <div className="lower-grid">
          <section className="learning">
            <h2>Continue learning</h2>
            {sidebar.isLoading ? (
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

          <HomeUpNextPanel items={model.upNext} />
        </div>
      </div>
    </div>
  )
}
