import { Link } from 'react-router-dom'

import { V2Brand } from '../../v2-shell/components/V2Brand'

export function PrivacyPage() {
  return (
    <main className="v2-terms-page">
      <header>
        <V2Brand to="/" />
        <Link to="/">Back to Chanter</Link>
      </header>
      <article>
        <p className="terms-eyebrow">Pre-release privacy information</p>
        <h1>Privacy</h1>
        <p>
          Chanter uses account profiles, course participation, messages, questions, uploaded
          files and assistant history to run learning communities. Access depends on your
          membership and role. Content shared with a course or community is available to
          its authorized participants.
        </p>
        <h2>Your controls</h2>
        <p>
          In <Link to="/app/account-data">Account data</Link>, you can request an export or
          prepare account deletion. Exports include your data and shared content you can
          still access, with omissions explained in the archive. Download access expires
          24 hours after the request; cleanup can continue afterward. Exporting and
          confirming deletion require a recent sign-in.
        </p>
        <h2>Deleting an account or course content</h2>
        <p>
          Preparing account deletion checks ownership and can be cancelled. Confirmation
          closes account access and starts irreversible cleanup. A request is complete only
          when the required services and recovery records acknowledge it. Pending work or
          failed delivery can delay completion; no fixed completion time is promised.
          The account deletion receipt cookie expires seven days after preparation.
          It can only read that request&apos;s status in its browser and cannot sign you in.
        </p>
        <p>
          Some records remain after cleanup: restricted moderation evidence; usage and
          assistant-attempt accounting with removed account and provider-request attribution;
          shared courses and assistant installations under the current Study Server owner;
          and minimal identifiers and file-storage metadata needed to enforce deletions
          after recovery. Other people&apos;s independently authored content remains theirs.
          A completed deletion does not mean every record has been erased.
        </p>
        <h2>Backups and recovery</h2>
        <p>
          Encrypted backups can contain earlier data. Recovery must reapply newer deletions
          before restoring access. Database backup retention depends on successful replacement
          backups and cleanup, so it is not a guaranteed fourteen-day erasure deadline.
          Configuration archives and deletion-enforcement records have separate retention
          requirements.
        </p>
        <h2>Sessions, providers and assistant requests</h2>
        <p>
          Chanter uses browser cookies for secure sessions and narrowly scoped export and
          deletion receipts. A pending cohort invitation stays in its browser tab while you
          sign in. Hosting, email, storage and any enabled sign-in or AI provider process
          the information needed for their configured function. AI requests can include your
          question and authorized course excerpts. Provider choice and any separate API
          billing are shown with the assistant controls.
        </p>
        <p>
          Optional error reporting excludes message and account content. If browser reporting
          is enabled, its receiver still observes network information such as your IP address.
          The enabled providers, their data handling and the operator&apos;s retention schedule
          must be published before public access opens.
        </p>
        <h2>Operator and contact</h2>
        <p>
          Public access is not open. The operator&apos;s identity, privacy contact and final
          provider details have not been configured. These details and the applicable
          retention terms must be completed before launch.
        </p>
      </article>
    </main>
  )
}
