# Account data frontend integration

Owning issue #251 / PR #335 provides the backend. #339 may implement the account deletion UI against this contract, reusing the committed `features/account-data` export UI and its exact lazy bundle allowance. Do not change backend authority based on fixture responses. Server/resource public progress routes are not yet a stable handoff.

## Account deletion

All routes use `/api/v1/auth/account/deletions`. Ordinary reads and mutations use the existing authenticated API client. Mutations retain its browser CSRF protection.

- `POST /` with `{requestId: <stable client UUID>}` returns 202 and Job. requestId is exactly the job ID. Reuse that UUID after an uncertain response. This prepares only; it does not delete. Actual login within five minutes is required, and passive refresh does not qualify. HTTP 428 asks the user to sign in again.
- `GET /{id}` returns Job for the current account.
- `POST /{id}/confirm` with `{confirmation: "DELETE MY ACCOUNT"}` returns 202. Only unexpired PREPARED may confirm, with the same recent-login requirement. Confirmation is irreversible and revokes the account sessions atomically with terminal authority. Do not retry confirmation by making a new job after an uncertain response; read its receipt.
- `DELETE /{id}` returns Job. It requests cancellation before confirmation. CANCELLING still holds preparation until ownership release arrives. ERASING cannot cancel.
- `GET /{id}/receipt` is a read-only same-origin request with `credentials: 'include'`, without the ordinary session-refresh client. The preparation response sets its opaque HttpOnly, Secure, SameSite=Strict cookie at this exact path, for seven days. The UI never reads or persists that credential. The receipt contains no account/content identity and cannot restore access or mutate anything. Wrong job, absent or expired cookie returns 404. Retain only the opaque job UUID to revisit progress after session revocation.

Job fields: `id`, `state`, `createdAt`, `preparationExpiresAt`, `replicationPending`, nullable `preparationError`, and `parts: [{source,state,errorCode}]`. Sources are auth/community/message/media/agent/search/notification. Part states are PENDING, COMPLETE or PRESERVED. `DELIVERY_FAILED` means delivery needs attention, not deletion completion.

States and truthful copy:

- PREPARING: checking ownership, no deletion yet.
- BLOCKED_OWNERSHIP: transfer or delete owned Study Servers before retrying preparation. Cancel this preparation before starting a new one; do not claim automatic ownership transfer.
- PREPARED: ready for explicit confirmation until preparationExpiresAt.
- PREPARATION_EXPIRED: preparation expired; cancellation/release must finish before replacement.
- CANCELLING / CANCELLED: release pending / preparation cancelled.
- ERASING: account access is closed; source cleanup remains in progress.
- WAITING_FOR_REPLICA: source dispositions are recorded, but durable external recovery authority is still pending.
- COMPLETE: all required source dispositions and external authority acknowledgement are present. PRESERVED means the fixed restricted moderation records were retained; it does not mean every record was erased.

Use one in-flight action, cancellation on unmount/account change, explicit refresh and stale-response fencing. Show ownership, irreversible confirmation and retention wording before confirm. After confirm, clear ordinary authenticated account state but keep a receipt view reachable without the signed-in route guard. No automatic success message on 202 or sign-out. HTTP 409 asks the user to refresh the current job; 429 is bounded admission, not an invitation to create many IDs. The UI must not invent a retention deadline, legal contact, policy approval or completion date.

## Export

Reuse `account-data-api.ts` and AccountDataPage. Existing authenticated `/api/v1/auth/account/exports` supports POST `{requestId}`, GET list, GET `/{id}` and DELETE `/{id}`. Job states BUILDING, READY, CANCELLED, EXPIRED and `cleanupPending` remain distinct. A READY export expires after 24 hours. Only actual recent login permits creation.

Download uses authenticated, CSRF-protected `POST /{id}/download-authorization`, then a same-origin native anchor navigation to `GET /{id}/download`. The one-use HttpOnly cookie expires in at most 60 seconds and is tied to the live account/session/job. Do not use a JavaScript Blob or URL token. A cancelled/partial download needs new authorization; an anchor click cannot prove completion. The backend checks current source access throughout streaming and omits the successful ZIP ending on failure.

Preserve explicit export omissions/current-authority wording and native UNKNOWN usage provenance. Hosted tests must exercise mobile layout, keyboard/focus, cancel/confirm races, revoked-session receipt access, uncertain confirm, expired receipt and native streaming retry. Keep account-data chunks absent from initial landing/sign-in/Home transfer.
