import { Link } from 'react-router-dom'

import { V2Brand } from '../../v2-shell/components/V2Brand'

/** Product data summary. Deployment-specific legal review and contact details remain required. */
export function PrivacyPage() {
  return (
    <main className="v2-terms-page">
      <header>
        <V2Brand to="/" />
        <Link to="/">Back to Chanter</Link>
      </header>
      <article>
        <p className="terms-eyebrow">Beta data summary. Legal review pending.</p>
        <h1>Privacy Policy</h1>
        <p>
          Chanter stores account profile data, course participation, messages, and support
          questions you submit so learning communities can operate. This summary does not identify
          a verified operating organization, hosting location, or legally reviewed retention policy.
        </p>
        <h2>Your information</h2>
        <p>
          Signed-in users can request an export from Account data. Private export copies expire
          after 24 hours. Shared content requires current access, and each archive explains its omissions.
          Exporting or cancelling an export does not delete your account.
        </p>
        <p>
          Backup copies and restricted moderation evidence have separate retention requirements.
          Chanter does not currently promise a fixed deadline for their removal. Do not treat a
          closed download or revoked session as confirmation that all data has been erased.
        </p>
        <h2>Contact</h2>
        <p>
          A verified privacy and support contact has not been configured for this deployment.
          Operator details and a reviewed privacy notice are required before public launch.
        </p>
        <h2>Commerce</h2>
        <p>
          Course storefront / paid enrollment commerce is post-MVP and not part of this beta.
        </p>
      </article>
    </main>
  )
}
