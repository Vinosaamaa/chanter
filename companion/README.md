# Native companion developer package

This issue-316 package runs the restricted Codex transport behind signed, single-use requests and explicit native approval. It is an unsigned `0.1.0-dev` source installation for Windows, Node 24, and a user-installed unmodified Codex 0.153.4. Other provider versions and native platforms fail closed. Linux CI exercises the platform-neutral request/state code, not native Codex isolation.

The backend issuer and desktop connection UI support the protected quoted-evidence flow. Deployment key provisioning, eligible-account production proof, and the Claude adapter remain external delivery gates. This is an unsigned developer package; it does not claim OS-trusted signing or verified subscription eligibility.

## Install and run

The operator supplies an exact HTTPS deployment origin and that deployment's Ed25519 **public** key in PEM format. The private signing key never belongs in this application. Choose a new, absolute installation directory and the absolute path to the provider-owned executable.

```powershell
node companion/cli.mjs install --directory <absolute-install-directory> --origin <https-deployment-origin> --public-key <deployment-public-key.pem> --codex <absolute-codex-executable>
node companion/cli.mjs status --directory <absolute-install-directory>
node companion/cli.mjs start --directory <absolute-install-directory>
```

The installation also contains `start.ps1` for launching the copied package in a visible PowerShell terminal. Installation copies only the companion program; it does not change PATH, register a service, schedule startup, download Codex, or sign in. An existing installation is preserved and replacement is refused. If installation is interrupted, choose a new empty directory; preserve the incomplete directory for operator inspection. The installer does not overwrite or delete uncertain existing state. Setup status checks the configuration and binary version; it explicitly reports account/listener status as unchecked. Use the running terminal's `status` command for current provider state.

The terminal accepts:

- `status`: inspect sanitized account category, advertised models, and known/unknown usage windows.
- `pair <signed-ticket>`: verify the website's short-lived pairing command, display its origin and user, and ask for a random approval challenge. Paste the printed JSON result back into that deployment's connection panel. The handle lasts five minutes and is revoked by restart/logout/revoke. Pairing itself does not authorize a study turn.
- `revoke`: invalidate the current pairing handle.
- `login`: explicitly start Codex's provider-owned device flow. Its URL and temporary device code appear only in this native terminal. Complete provider sign-in, then answer the local completion challenge, or enter `deny` to cancel.
- `logout`: revoke pairing, cancel an active operation, and ask the provider to sign out of this companion's separate authentication store.
- `restart`: stop the listener and current operation, revoke pairing, and start again with the same installation identity and consumed-request records.
- `stop`, Ctrl+C, or terminal input closure: stop the listener, cancel the owned provider process, finish retention cleanup, and exit.

Every study request displays the exact provider/model, question identifier, and supplied input as escaped untrusted text. Only the displayed random `approve …` challenge accepts it. `deny`, expiry, disconnection, restart, or stop rejects the pending action. Redirected stdin/stdout cannot approve requests. Evidence displayed in the terminal may remain in the operator's terminal scrollback; this package does not write a transcript file.

Generation also requires a provider-owned subscription account, an advertised model, and known unexhausted primary/secondary limits. Unknown limits, unsupported authentication, or a depleted window fail closed. Chanter never purchases/reset credits or falls back to an API key. A plan label is not a guarantee about charges; provider account policy remains authoritative.

## Retention and recovery

The authentication home is private and persistent. Only the provider reads/writes its credentials; Chanter neither opens nor copies authentication files. Supported `sqlite_home`, `CODEX_SQLITE_HOME`, and `log_dir` controls move operational databases and logs into a unique per-operation directory. The provider process must exit before that owned directory and its temporary executable wrappers are removed. See the [official environment reference](https://developers.openai.com/es-419/docs/config-file/environment-variables) for SQLite placement.

Each operation records its installation and controller/provider process identifiers locally. Startup may remove an owned orphan only when both recorded processes are absent. A live, ambiguous, unrecognized, linked, or unremovable directory blocks operation with `NATIVE_RETENTION_BLOCKED`. No unrelated process is killed, and no ambiguous directory is silently deleted. Operator review is required for that state. Credentials and the durable single-use request database are preserved. The latter contains identifiers, hashes, and pairing metadata, not evidence text; its current 10,000-claim limit fails closed pending the final retention policy.

Private NTFS access rules are required on Windows. Existing state with broadened access is rejected instead of repaired. The module refuses unsupported provider versions and changed effective isolation before sending evidence. The observed fixed skills namespace still exists, with empty catalogs and denied read/exec canaries; this is not a universal zero-tools claim. Account-specific inference remains a release gate.

## Deployment and browser connection

The auth service issues access tokens with the existing durable browser-session ID. The agent checks that live session through authenticated private introspection before releasing each capability and accepting a result. Legacy access tokens without a session ID still work for existing web endpoints but cannot authorize native access. Refresh preserves the session ID; logout, refresh replay, expiry, and revocation invalidate native authority. Keep the auth introspection endpoint private.

Configure these agent-service variables together: `CHANTER_NATIVE_COMPANION_ORIGIN` (exact HTTPS origin), `CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8` (base64 DER Ed25519 private key), `CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI` (base64 DER matching public key), and `CHANTER_NATIVE_COMPANION_MODELS` (comma-separated allowed native model identifiers). Empty configuration disables the capability; partial or invalid configuration fails startup. `AUTH_SERVICE_URL` points to the private auth service, using the existing internal service token. The gateway receives no internal token. Install only the matching public PEM in the native package. Rotate deployment keys with explicit public-key reprovisioning; there is no browser-supplied key override.

Deploy auth, the agent's V11 metadata and V12 outbox migrations, message V10 consumer migration, and gateway routing before enabling configuration. No auth schema migration is needed. Agent V9/V10 belong to #246 and must precede these migrations; do not enable Flyway out-of-order migration. The metadata row binds a shared one-attempt reservation to user/session/installation/question/model and a bounded evidence snapshot. Acceptance repeats current grants/resource authorization and quote validation; client token counts never refund the reservation. Evidence is cleared after acceptance/rejection and by minute-based expiry cleanup, including a bounded grace period for abandoned acceptance.

Production epoch 7 rejects older writers that omit native claims or atomic answer/status events. The deployment preflight leaves the issuer disabled by absence and validates the complete optional configuration only in the private agent environment. See [production configuration](../docs/operations/production-deploy.md#optional-native-companion-issuer). The public verifier key is installed explicitly; no signing or provider credential is copied to another backend service.

The accepted answer and an IDs-only status event commit together. `MESSAGE_SERVICE_URL` must resolve to the private message service; the shared dispatcher sends authenticated events to `/api/v1/internal/events`. A message outage leaves the answer accepted and status delivery pending. Existing outbox failure/replay operations recover delivery without retrying the provider. The consumer rechecks current question-author access and preserves human or closed outcomes. Deploy the message consumer before enabling native issuance.

In a Windows browser, expand **Connect my Codex account on this computer**, check deployment availability, enter the installation ID, and create the terminal pairing command. Approve its native challenge and paste the returned JSON. **Check connection** obtains a signed single-use status capability and intersects live advertised models with the deployment allowlist. Discovery is user-triggered; it is also rechecked immediately before generation. No background polling consumes claims. The loopback URL is fixed to `http://127.0.0.1:43160`; browser use requires the installer's default port. Local-network browser permissions may still prevent access. Mobile and unavailable deployments retain the web API/source choices.

Each question needs separate website export consent and native terminal approval. The browser sends only a pairing handle and signed capability to loopback, never its Chanter bearer token or cookies. Client deltas are not displayed as an answer. The backend accepts and saves only authorized source quotations. Cancellation, logout, navigation, or uncertain completion does not retry with another provider.

## Source package provenance

The native boundary workflow packages the exact checked-out companion source and SHA-256 checksum after Windows/Linux tests. An explicit workflow dispatch or accepted-main run also creates a free GitHub artifact attestation. Download the package from the run for the reviewed commit, compare `SOURCE_COMMIT.txt` to that commit, verify its checksum, and run `gh attestation verify chanter-native-source.zip --repo Vinosaamaa/chanter`. Inspect the verified source commit and workflow identity before installation. Pull-request artifacts have checksums but no attestation until a trusted dispatch. See [GitHub's artifact attestation documentation](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations). This proves workflow/source provenance; it is not Authenticode, an OS trust grant, or account eligibility.

## Verification

```powershell
node --test companion/tests/*.test.mjs
# To additionally run local no-login synthetic-provider/native tests:
$env:CHANTER_CODEX_TEST_BINARY = <absolute-supported-codex-executable>
node --test companion/tests/*.test.mjs
```

Without that explicit executable, native tests skip. The hosted Windows/Linux workflow never downloads a provider binary, logs in, or invokes an external model. Test fixtures use generated signing keys and loopback model transport. They do not authorize production evidence export or verify an eligible subscription.
