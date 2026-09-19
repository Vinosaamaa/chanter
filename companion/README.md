# Native companion developer package

This issue-316 package runs the restricted Codex transport behind signed, single-use requests and explicit native approval. It is an unsigned `0.1.0-dev` source installation for Windows, Node 24, and a user-installed unmodified Codex 0.153.4. Other provider versions and native platforms fail closed. Linux CI exercises the platform-neutral request/state code, not native Codex isolation.

Chanter's backend capability issuer, deployment signing-key provisioning, hosted connection UI, signed installers, Claude adapter, and eligible-account production proof remain incomplete. The operator can install/start/inspect/restart/stop this package now. A synthetic key or protocol fixture does not establish account eligibility or a real Chanter integration.

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
- `pair <signed-ticket>`: verify a deployment-issued pairing ticket, display its origin and user, and ask for a random approval challenge. The returned expiring handle must reach the deployment's native client through its pairing flow. The hosted Chanter UI is not wired yet.
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

Private NTFS access rules are required on Windows. Existing state with broadened access is rejected instead of repaired. The module refuses unsupported provider versions and changed effective isolation before sending evidence. The observed fixed skills namespace still exists, with empty catalogs and denied read/exec canaries; this is not a universal zero-tools claim. Account-specific inference and signing remain release gates.

## Verification

```powershell
node --test companion/tests/*.test.mjs
# To additionally run local no-login synthetic-provider/native tests:
$env:CHANTER_CODEX_TEST_BINARY = <absolute-supported-codex-executable>
node --test companion/tests/*.test.mjs
```

Without that explicit executable, native tests skip. The hosted Windows/Linux workflow never downloads a provider binary, logs in, or invokes an external model. Test fixtures use generated signing keys and loopback model transport. They do not authorize production evidence export or verify an eligible subscription.
