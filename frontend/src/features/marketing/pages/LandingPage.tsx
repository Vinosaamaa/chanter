import { BarChart3, Hash, Sparkles, UsersRound } from 'lucide-react'
import { Link } from 'react-router-dom'

import { MarketingHeader, MarketingHero } from '../components/MarketingHeader'
import { MarketingProductPreview } from '../components/MarketingProductPreview'
import { useStudyServerCta } from '../hooks/use-study-server-cta'
import { MARKETING_FEATURES, MARKETING_PRICING_TEASER, MARKETING_USE_CASES } from '../marketing-content'
import { MARKETING_SIGN_IN_PATH } from '../marketing-routes'

import '../design/marketing-v2.css'

const FEATURE_ICONS = {
  'ai-assistant': Sparkles,
  'course-channels': Hash,
  'ta-queue': UsersRound,
  'instructor-dashboard': BarChart3,
} as const

export function LandingPage() {
  const createServerCta = useStudyServerCta()

  return (
    <div className="marketing-v2">
      <MarketingHeader />

      <main>
        <section className="marketing-v2-hero" aria-labelledby="marketing-title">
          <div className="marketing-v2-hero-inner marketing-v2-hero-split">
            <div>
              <MarketingHero />
            </div>
            <MarketingProductPreview />
          </div>
        </section>

        <section className="marketing-v2-features" id="features">
          <div className="marketing-v2-section-inner">
            <div className="marketing-v2-section-heading">
              <h2>Stay close to what you are learning.</h2>
              <span>
                Give learners clear places to learn and ask for help. Give teaching teams the
                context and tools to respond well.
              </span>
            </div>

            <div className="marketing-v2-feature-grid">
              {MARKETING_FEATURES.map((feature) => {
                const Icon = FEATURE_ICONS[feature.id]
                return (
                  <article className={`marketing-v2-feature marketing-v2-feature-${feature.id}`} key={feature.id}>
                    <span className="marketing-v2-feature-icon" aria-hidden="true">
                      <Icon />
                    </span>
                    <h3>{feature.title}</h3>
                    <p>{feature.description}</p>
                  </article>
                )
              })}
            </div>
          </div>
        </section>

        <section className="marketing-v2-use-cases" id="use-cases">
          <div className="marketing-v2-section-inner marketing-v2-use-cases-inner">
            <div className="marketing-v2-section-heading marketing-v2-section-heading-dark">
              <h2>Built around people who learn together.</h2>
            </div>
            <div className="marketing-v2-use-case-list">
              {MARKETING_USE_CASES.map((useCase) => (
                <article key={useCase}>
                  <h3>{useCase}</h3>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="marketing-v2-pricing" id="pricing">
          <div className="marketing-v2-section-inner marketing-v2-pricing-inner">
            <div>
              <h2>{MARKETING_PRICING_TEASER.headline}</h2>
              <p>{MARKETING_PRICING_TEASER.body}</p>
            </div>
            <div className="marketing-v2-pricing-actions">
              <Link className="marketing-v2-primary" to={createServerCta.to} state={createServerCta.state}>
                Create Study Server
              </Link>
              <Link className="marketing-v2-light-link" to={MARKETING_SIGN_IN_PATH}>
                Sign in
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="marketing-v2-footer">
        <div className="marketing-v2-section-inner marketing-v2-footer-inner">
          <span>Chanter</span>
          <p>Learning communities with teaching support built in.</p>
          <div className="marketing-v2-footer-meta">
            <p>
              <Link to="/terms">Terms</Link>
              {' · '}
              <Link to="/privacy">Privacy</Link>
              {' · '}
              Support contact not yet configured
            </p>
            <p>{new Date().getFullYear()} Chanter</p>
          </div>
        </div>
      </footer>
    </div>
  )
}
