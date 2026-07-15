import { BarChart3, Check, GraduationCap, Hash, ShieldCheck, Sparkles, UsersRound, Zap } from 'lucide-react'
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

const TRUST_POINTS = [
  { icon: GraduationCap, label: 'Free for educators' },
  { icon: ShieldCheck, label: 'Student privacy first' },
  { icon: Zap, label: 'Ready in minutes' },
] as const

export function LandingPage() {
  const createServerCta = useStudyServerCta()

  return (
    <div className="marketing-v2">
      <MarketingHeader />

      <main>
        <section className="marketing-v2-hero" aria-labelledby="marketing-title">
          <div className="marketing-v2-hero-atmosphere" aria-hidden="true" />
          <div className="marketing-v2-hero-inner marketing-v2-hero-split">
            <div>
              <MarketingHero />
              <ul className="marketing-v2-trust" aria-label="Product benefits">
                {TRUST_POINTS.map(({ icon: Icon, label }) => (
                  <li key={label}>
                    <Icon aria-hidden="true" />
                    {label}
                  </li>
                ))}
              </ul>
            </div>
            <MarketingProductPreview />
          </div>
        </section>

        <section className="marketing-v2-features" id="features">
          <div className="marketing-v2-section-inner">
            <div className="marketing-v2-section-heading">
              <p>One workspace</p>
              <h2>Everything a learning community needs to move forward</h2>
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
                    <a href="#pricing">
                      Learn more
                      <span aria-hidden="true">+</span>
                    </a>
                  </article>
                )
              })}
            </div>
          </div>
        </section>

        <section className="marketing-v2-use-cases" id="use-cases">
          <div className="marketing-v2-section-inner marketing-v2-use-cases-inner">
            <div className="marketing-v2-section-heading marketing-v2-section-heading-dark">
              <p>Built around the course</p>
              <h2>Less tool switching. More time for teaching and learning.</h2>
            </div>
            <div className="marketing-v2-use-case-list">
              {MARKETING_USE_CASES.map((useCase, index) => (
                <article key={useCase}>
                  <span>0{index + 1}</span>
                  <h3>{useCase}</h3>
                  <p>
                    <Check aria-hidden="true" />
                    Courses, support, community, and live sessions together
                  </p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="marketing-v2-pricing" id="pricing">
          <div className="marketing-v2-section-inner marketing-v2-pricing-inner">
            <div>
              <p className="marketing-v2-pricing-label">Start today</p>
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
              Beta support: support@chanter.example
            </p>
            <p>{new Date().getFullYear()} Chanter</p>
          </div>
        </div>
      </footer>
    </div>
  )
}
