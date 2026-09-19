# Issue #249 change log

The implementation scope is recorded on #249. Work starts from accepted main `5d3b154` in the dedicated moderation branch. The initial read found partial blocking and friend-presence filtering, but no unblock, reports, platform roles, privileged audit or account suspension state.

The design keeps moderation inside auth-service with source-owned evidence checks, separately granted operator roles and reversible restrictions. Gateway routing and admission belong to #253. Session-bound token/introspection changes belong to #316 and must be preserved. No production operator enrollment or public release is claimed.

The first TDD slices add separate operator grants, authenticator enrollment and replay protection, encrypted factors, assigned-case evidence reads, audit records and timed restrictions. Current status checks protect auth session issuance/refresh and the authenticated handlers in seven services. Realtime revalidates incoming actions and idle connections. A close-frame race found by the real WebSocket test was fixed before proceeding.

The report client uses fixed source-service endpoints and bounded evidence snapshots. Message evidence requires a saved DM participant or current channel permission. Blocking does not erase the participant's right to report historic DMs. The foundation checkpoint compiles every backend and passes 30 focused tests, including three real WebSocket close repetitions; one PostgreSQL-only test is skipped locally. PostgreSQL audit/note immutability has a dedicated real-database regression; its hosted execution is still pending. Other source integration, restriction actions, appeals and UI remain in progress. The complete acceptance boundary is tracked in the system review.
