import { BookOpen, FolderOpen, HelpCircle, MessageSquare } from 'lucide-react'
import { Link } from 'react-router-dom'

import { v2HomePath } from '../v2-routes'
import { useAuthStore } from '../../../stores/auth-store'

const featureCards = [
  { icon: MessageSquare, title: 'Chat', description: 'Course-wide discussion' },
  { icon: HelpCircle, title: 'Questions', description: 'Ask peers, TAs & AI' },
  { icon: FolderOpen, title: 'Resources', description: 'Slides, notes, recordings' },
]

export function WelcomeJoinedPage() {
  const user = useAuthStore((state) => state.user)
  const firstName = user?.displayName?.split(' ')[0]

  return (
    <section className="v2-welcome-page">
      <div className="welcome-reading">
        <BookOpen size={32} color="#2458d3" aria-hidden="true" />
        <h1>Welcome to Chanter{firstName ? `, ${firstName}` : ''}.</h1>
        <p>Find your Courses on Home, get to know your learning community, and make yourself at home.</p>
        <div className="welcome-features">
          {featureCards.map(({ icon: Icon, title, description }) => (
            <article key={title}><span><Icon size={24} /></span><h2>{title}</h2><p>{description}</p></article>
          ))}
        </div>
        <Link className="v2-primary-button" to={v2HomePath()}>Go to Home</Link>
      </div>
    </section>
  )
}
