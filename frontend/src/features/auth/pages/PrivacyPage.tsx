import { Link } from 'react-router-dom'

import { V2Brand } from '../../v2-shell/components/V2Brand'

/** Placeholder privacy policy for public beta (#104). */
export function PrivacyPage() {
  return (
    <main className="v2-terms-page">
      <header>
        <V2Brand to="/" />
        <Link to="/">Back to Chanter</Link>
      </header>
      <article>
        <p className="terms-eyebrow">Beta placeholder — July 2026</p>
        <h1>Privacy Policy</h1>
        <p>
          Chanter stores account profile data, course participation, messages, and support
          questions you submit so learning communities can operate. During public beta we keep
          data on the staging or operator-controlled host described in the staging deploy runbook.
        </p>
        <h2>Contact</h2>
        <p>
          Questions about privacy during beta: <a href="mailto:support@chanter.example">support@chanter.example</a>.
        </p>
        <h2>Commerce</h2>
        <p>
          Course storefront / paid enrollment commerce is post-MVP and not part of this beta.
        </p>
      </article>
    </main>
  )
}
