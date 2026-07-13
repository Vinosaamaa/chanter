import { ArrowRight, Menu, Play, X } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { V2Brand } from '../../v2-shell/components/V2Brand'
import { useStudyServerCta } from '../hooks/use-study-server-cta'
import { MARKETING_DEMO_PATH, MARKETING_DOCS_URL, MARKETING_SIGN_IN_PATH } from '../marketing-routes'

const NAV_ITEMS = [
  { label: 'Features', href: '#features' },
  { label: 'Use cases', href: '#use-cases' },
  { label: 'Pricing', href: '#pricing' },
] as const

export function MarketingHeader() {
  const [menuOpen, setMenuOpen] = useState(false)
  const createServerCta = useStudyServerCta()

  return (
    <header className="marketing-v2-header">
      <div className="marketing-v2-nav">
        <V2Brand to="/" className="marketing-v2-brand" />

        <nav aria-label="Marketing" className="marketing-v2-nav-links">
          {NAV_ITEMS.map((item) => (
            <a key={item.href} href={item.href}>
              {item.label}
            </a>
          ))}
          <a href={MARKETING_DOCS_URL} rel="noreferrer" target="_blank">
            Docs
          </a>
        </nav>

        <div className="marketing-v2-actions">
          <Link className="marketing-v2-sign-in" to={MARKETING_SIGN_IN_PATH}>
            Sign in
          </Link>
          <Link className="marketing-v2-primary marketing-v2-header-cta" to={createServerCta.to} state={createServerCta.state}>
            Get started
          </Link>
          <button
            aria-controls="marketing-mobile-menu"
            aria-expanded={menuOpen}
            aria-label={menuOpen ? 'Close navigation' : 'Open navigation'}
            className="marketing-v2-menu-button"
            onClick={() => setMenuOpen((open) => !open)}
            type="button"
          >
            {menuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
          </button>
        </div>
      </div>

      {menuOpen ? (
        <nav aria-label="Mobile marketing" className="marketing-v2-mobile-nav" id="marketing-mobile-menu">
          {NAV_ITEMS.map((item) => (
            <a key={item.href} href={item.href} onClick={() => setMenuOpen(false)}>
              {item.label}
            </a>
          ))}
          <a href={MARKETING_DOCS_URL} rel="noreferrer" target="_blank">
            Docs
          </a>
          <Link to={MARKETING_SIGN_IN_PATH} onClick={() => setMenuOpen(false)}>
            Sign in
          </Link>
        </nav>
      ) : null}
    </header>
  )
}

export function MarketingHero() {
  const createServerCta = useStudyServerCta()

  return (
    <div className="marketing-v2-hero-copy">
      <p className="marketing-v2-eyebrow">Built for educators. Designed for learners.</p>
      <h1 id="marketing-title">Chanter</h1>
      <p className="marketing-v2-tagline">Your learning community, finally in one place.</p>
      <p className="marketing-v2-intro">
        Bring courses, conversations, grounded AI support, office hours, and teaching operations
        together in one focused workspace.
      </p>

      <div className="marketing-v2-hero-actions">
        <Link className="marketing-v2-primary" to={createServerCta.to} state={createServerCta.state}>
          Create Study Server
          <ArrowRight aria-hidden="true" />
        </Link>
        <Link className="marketing-v2-secondary" to={MARKETING_DEMO_PATH}>
          <Play aria-hidden="true" />
          View demo
        </Link>
      </div>
    </div>
  )
}
