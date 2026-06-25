import { Link } from 'react-router-dom'

export function DevDemoRoutePage() {
  return (
    <div className="border-b border-amber-500/40 bg-amber-500/10 px-4 py-2 text-sm text-amber-100">
      <p>
        Development API demo harness — not production UI.{' '}
        <Link className="font-medium text-amber-50 underline" to="/app">
          Return to app shell
        </Link>
      </p>
    </div>
  )
}
