import { Link } from 'react-router-dom'

import { Button } from '../../../components/ui/button'
import { Card, CardDescription, CardTitle } from '../../../components/ui/card'
import { useAuthStore } from '../../../stores/auth-store'
import { AppPreviewMock } from '../components/AppPreviewMock'
import { MarketingHeader, MarketingHero } from '../components/MarketingHeader'
import {
  MARKETING_FEATURES,
  MARKETING_PRICING_TEASER,
  MARKETING_USE_CASES,
} from '../marketing-content'
import { createStudyServerCta, MARKETING_SIGN_IN_PATH } from '../marketing-routes'

export function LandingPage() {
  const isAuthenticated = Boolean(useAuthStore((state) => state.accessToken))
  const createServerCta = createStudyServerCta(isAuthenticated)

  return (
    <div className="min-h-screen bg-app-bg">
      <MarketingHeader />

      <main>
        <section className="mx-auto max-w-6xl px-6 pb-16 pt-10 md:pt-14">
          <div className="grid gap-10 lg:grid-cols-2 lg:items-center">
            <MarketingHero />
            <AppPreviewMock />
          </div>
        </section>

        <section id="features" className="border-t border-app-border bg-app-surface/40 py-16">
          <div className="mx-auto max-w-6xl px-6">
            <div className="max-w-2xl">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">Features</p>
              <h2 className="mt-2 text-3xl font-semibold text-app-text">
                Everything your learning community needs
              </h2>
              <p className="mt-2 text-app-muted">
                Course channels, grounded AI, support operations, and instructor analytics in one
                product shell.
              </p>
            </div>

            <div className="mt-10 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {MARKETING_FEATURES.map((feature) => (
                <Card key={feature.id} className="flex h-full flex-col">
                  <span aria-hidden className="text-2xl">
                    {feature.icon}
                  </span>
                  <CardTitle className="mt-3">{feature.title}</CardTitle>
                  <CardDescription className="flex-1">{feature.description}</CardDescription>
                  <a
                    href="#pricing"
                    className="mt-4 text-sm font-medium text-app-accent hover:text-app-accent-hover"
                  >
                    Learn more →
                  </a>
                </Card>
              ))}
            </div>
          </div>
        </section>

        <section id="use-cases" className="py-16">
          <div className="mx-auto max-w-6xl px-6">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">Use cases</p>
            <h2 className="mt-2 text-3xl font-semibold text-app-text">Built for modern teaching teams</h2>
            <ul className="mt-6 grid gap-3 md:grid-cols-3">
              {MARKETING_USE_CASES.map((useCase) => (
                <li
                  key={useCase}
                  className="rounded-xl border border-app-border bg-app-surface px-4 py-5 text-sm text-app-text"
                >
                  {useCase}
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section id="pricing" className="border-t border-app-border bg-app-surface/40 py-16">
          <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 px-6 md:flex-row md:items-center">
            <div className="max-w-xl">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">Pricing</p>
              <h2 className="mt-2 text-3xl font-semibold text-app-text">
                {MARKETING_PRICING_TEASER.headline}
              </h2>
              <p className="mt-2 text-app-muted">{MARKETING_PRICING_TEASER.body}</p>
            </div>
            <div className="flex flex-wrap gap-3">
              <Link to={createServerCta.to} state={createServerCta.state}>
                <Button>Create Study Server</Button>
              </Link>
              <Link to={MARKETING_SIGN_IN_PATH}>
                <Button variant="secondary">Sign in</Button>
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-app-border py-8">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-6 text-sm text-app-muted md:flex-row md:items-center md:justify-between">
          <p>© {new Date().getFullYear()} Chanter</p>
          <p>Discord for learning communities, with AI teaching assistants built in.</p>
        </div>
      </footer>
    </div>
  )
}
