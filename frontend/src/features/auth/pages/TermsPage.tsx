import { Link } from 'react-router-dom'

import { V2Brand } from '../../v2-shell/components/V2Brand'

export function TermsPage() {
  return (
    <main className="v2-terms-page">
      <header>
        <V2Brand to="/" />
        <Link to="/sign-in">Back to sign in</Link>
      </header>

      <article>
        <p className="terms-eyebrow">Effective July 13, 2026</p>
        <h1>Terms of Service</h1>
        <p>
          These terms govern your use of Chanter. By creating an account or using the service,
          you agree to follow these terms and the rules of the learning communities you join.
        </p>

        <h2>Your account</h2>
        <p>
          Provide accurate account information, keep your credentials secure, and tell us if you
          believe your account has been compromised. You are responsible for activity performed
          through your account.
        </p>

        <h2>Acceptable use</h2>
        <p>
          Do not misuse Chanter, interfere with the service, access another person&apos;s account,
          upload unlawful material, or use course content in a way that violates another
          person&apos;s rights.
        </p>

        <h2>Learning content</h2>
        <p>
          You retain ownership of content you submit. You grant Chanter permission to store,
          process, and display that content only as needed to operate and improve the service.
          Instructors and community owners remain responsible for the materials they publish.
        </p>

        <h2>Service availability</h2>
        <p>
          Chanter is under active development. Features may change, and the service may
          occasionally be unavailable. We work to protect your data and provide a reliable
          service, but we cannot guarantee uninterrupted operation.
        </p>

        <h2>Ending access</h2>
        <p>
          You may stop using Chanter at any time. We may suspend access when necessary to protect
          users, learning communities, or the service, including for serious or repeated
          violations of these terms.
        </p>

        <h2>Contact</h2>
        <p>Questions about these terms can be sent to support@chanter.app.</p>
      </article>
    </main>
  )
}
