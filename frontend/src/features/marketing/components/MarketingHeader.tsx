import { Link } from 'react-router-dom'

import { Button } from '../../../components/ui/button'
import { useStudyServerCta } from '../hooks/use-study-server-cta'
import { MARKETING_DEMO_PATH, MARKETING_DOCS_URL, MARKETING_SIGN_IN_PATH } from '../marketing-routes'

const NAV_ITEMS = [
  { label: 'Features', href: '#features' },
  { label: 'Use Cases', href: '#use-cases' },
  { label: 'Pricing', href: '#pricing' },
] as const

export function MarketingHeader() {
  const createServerCta = useStudyServerCta()

  return (
    <header className="sticky top-0 z-20 border-b border-app-border/80 bg-app-bg/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-4">
        <Link to="/" className="text-sm font-semibold tracking-wide text-app-text">
          <span className="text-app-accent">chanter</span>
        </Link>

        <nav aria-label="Marketing" className="hidden items-center gap-6 md:flex">
          {NAV_ITEMS.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className="text-sm text-app-muted transition-colors hover:text-app-text"
            >
              {item.label}
            </a>
          ))}
          <a
            href={MARKETING_DOCS_URL}
            className="text-sm text-app-muted transition-colors hover:text-app-text"
            rel="noreferrer"
            target="_blank"
          >
            Docs
          </a>
        </nav>

        <div className="flex items-center gap-2">
          <Link
            to={MARKETING_SIGN_IN_PATH}
            className="hidden rounded-md px-3 py-2 text-sm text-app-muted transition-colors hover:bg-app-elevated hover:text-app-text sm:inline-flex"
          >
            Sign in
          </Link>
          <Link to={createServerCta.to} state={createServerCta.state}>
            <Button>Get started</Button>
          </Link>
        </div>
      </div>
    </header>
  )
}

export function MarketingHero() {
  const createServerCta = useStudyServerCta()

  return (
    <div>
      <p className="inline-flex rounded-full border border-app-border bg-app-surface px-3 py-1 text-xs font-medium text-app-muted">
        Built for educators. Designed for students.
      </p>
      <h1 className="mt-4 text-4xl font-semibold tracking-tight text-app-text md:text-5xl lg:text-[3.25rem] lg:leading-tight">
        Discord for{' '}
        <span className="bg-gradient-to-r from-indigo-300 via-violet-300 to-sky-300 bg-clip-text text-transparent">
          learning communities
        </span>
      </h1>
      <p className="mt-4 max-w-xl text-lg text-app-muted">
        Chanter brings your course to life with organized channels, AI study support, TA queues, and
        powerful instructor tools — all in one collaborative space.
      </p>

      <div className="mt-6 flex flex-wrap gap-3">
        <Link to={createServerCta.to} state={createServerCta.state}>
          <Button className="gap-2 px-5">
            Create Study Server
            <span aria-hidden>→</span>
          </Button>
        </Link>
        <Link to={MARKETING_DEMO_PATH}>
          <Button variant="secondary" className="gap-2 px-5">
            <span aria-hidden>▶</span>
            View demo
          </Button>
        </Link>
      </div>

      <ul className="mt-6 flex flex-wrap gap-x-6 gap-y-2 text-sm text-app-muted">
        <li>Free for educators</li>
        <li>Student privacy first</li>
        <li>Easy to set up</li>
      </ul>
    </div>
  )
}
