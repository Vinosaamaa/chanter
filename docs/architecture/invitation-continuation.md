# Cohort invitation continuation

Issue #339 and PR #340 own this interaction. A learner following a cohort link must
retain that invitation while registering, verifying email and signing in. Previously
the invitation was stored only after authentication; verification returned to a bare
sign-in URL and lost it. The Google callback also skipped the invitation handler.

## Ownership and behavior

The sign-in page records complete cohort/invite parameters in the existing tab-local
session storage before authentication. A bare sign-in visit leaves that pending
invitation intact. The authenticated continuation consumes it and calls the existing
authorized cohort-join API. Backend enrollment policy remains authoritative.

The Google callback returns to authenticated sign-in after completing its existing
session exchange, so password and provider sign-in share the same continuation.
Each continuation shares one promise for its current search parameters and explicit
retry. React effect replay cannot consume an empty slot and navigate while the
original join is still pending. Existing transient-error retry behavior remains.

Registration tells invited learners to return to the original tab after verifying
their email. Same-tab verification also preserves the invitation through a bare
sign-in return. Session storage does not transfer an invitation into an independently
opened tab; that tab can reopen the original invitation link. No invitation code is
added to verification email, server-side auth state or cross-tab local storage.

## Verification boundaries

Three red/green regressions cover pre-auth retention, OAuth continuation routing and
effect replay while a join is unresolved. The real-service owner browser journey
also covers wizard Close/Back, manual enrollment, joining from the displayed invite,
and a new invited learner's UI registration, actual email verification, bare sign-in
and persistent course visibility. It disables credential-bearing artifacts.

Hosted browser execution must pass before this journey is accepted. Reading the
displayed invitation does not prove operating-system clipboard behavior. Mocked
OAuth exchange establishes callback routing only; real provider authentication
remains a deployment gate.
