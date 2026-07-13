import { Check, FolderOpen, HelpCircle, MessageSquare } from 'lucide-react'
import { Link } from 'react-router-dom'

import { HomePage } from './HomePage'
import { useV2SidebarData } from '../hooks/use-v2-sidebar-data'
import { v2CoursePath, v2HomePath } from '../v2-routes'
import { useAuthStore } from '../../../stores/auth-store'

const featureCards = [
  { icon: MessageSquare, title: 'Chat', description: 'Course-wide discussion' },
  { icon: HelpCircle, title: 'Questions', description: 'Ask peers, TAs & AI' },
  { icon: FolderOpen, title: 'Resources', description: 'Slides, notes, recordings' },
]

export function WelcomeJoinedPage() {
  const sidebar = useV2SidebarData()
  const user = useAuthStore((state) => state.user)
  const course = sidebar.allCourses[0]
  const firstName = user?.displayName?.split(' ')[0] ?? 'Sam'
  const courseCode = course?.title.split(' — ')[0] ?? 'CS 101'
  const courseHref = course
    ? v2CoursePath(course.serverId, course.id, 'overview')
    : v2HomePath()

  return (
    <div className="v2-overlay-page">
      <HomePage />
      <div className="v2-modal-backdrop welcome-backdrop" role="presentation">
        <span className="confetti confetti-one" />
        <span className="confetti confetti-two" />
        <span className="confetti confetti-three" />
        <section className="welcome-modal" role="dialog" aria-modal="true" aria-labelledby="welcome-title">
          <div className="welcome-course-badge">
            {courseCode.replace(/\s/g, '')}
            <span><Check size={20} /></span>
          </div>
          <h1 id="welcome-title">Welcome to {courseCode}, {firstName}!</h1>
          <p>You&apos;re enrolled in the Spring cohort · Dr. Alex Johnson · 128 students</p>

          <div className="welcome-features">
            {featureCards.map(({ icon: Icon, title, description }) => (
              <article key={title}>
                <span><Icon size={30} /></span>
                <h2>{title}</h2>
                <p>{description}</p>
              </article>
            ))}
          </div>

          <div className="welcome-checklist">
            <h2>Get set up</h2>
            <p className="complete"><span><Check size={17} /></span> Join {courseCode}</p>
            <p><span /> Say hi in Chat</p>
            <p><span /> Ask your first question</p>
          </div>

          <Link className="v2-primary-button welcome-primary" to={courseHref}>
            Go to {courseCode}
          </Link>
          <Link className="welcome-skip" to={v2HomePath()}>Skip to Home</Link>
        </section>
      </div>
    </div>
  )
}
