# Reproducible deployment on the free resource budget

Issue [#243](https://github.com/Vinosaamaa/chanter/issues/243) packages the existing Java/React product for one Linux ARM64 VM. This is a repository implementation and operator runbook, not a claim that a public deployment exists. No hosting, DNS or email account has been provisioned. The secure browser-session and SMTP release from [#242](https://github.com/Vinosaamaa/chanter/issues/242) must be integrated before building these images.

## Hosting decision and limits

Use one **unupgraded Oracle Always Free A1 instance, 2 OCPUs and 12 GB RAM**, with a 100 GB boot volume in the account's home region. Oracle's current allowance is 1,500 OCPU-hours and 9,000 GB-hours monthly, equivalent to this continuously running allocation, with 200 GB total boot/block storage. Capacity can be unavailable; idle instances can be reclaimed. These are free-tier limits, not availability or durability guarantees. Never substitute a paid shape, upgrade the account, enable automatic payment or create resources against trial credits to get past a quota error. See [Oracle's current Always Free resource limits](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm).

Account creation, identity/card verification if requested by the provider, unused free capacity and a hostname controlled by the owner are external prerequisites. Domain purchase is outside the zero-spend constraint. A placeholder or an unverified public wildcard DNS service is not a production hostname. If these prerequisites cannot be satisfied without spending, stop at the completed release package.

Render's free service allocation and expiring free PostgreSQL do not fit ten persistent Java services. Its free instances also block outbound SMTP ports. See [Render's documented free-service restrictions](https://render.com/docs/free). Self-managed PostgreSQL and Redis on the VM replace the issue's original managed-service assumptions. This introduces host failure risk and scheduled deployment downtime; there is no high availability.

| Runtime | Memory limit |
| --- | ---: |
| Ten Java services together | 6,144 MiB |
| PostgreSQL | 1,024 MiB |
| Redis | 256 MiB |
| LiveKit | 256 MiB |
| Caddy and static frontend | 128 MiB |
| Total running containers | 7,808 MiB |

The remaining host memory covers Linux, Docker and filesystem cache. Java uses half each container's memory for heap, at most two processors, bounded direct memory and five database connections per service. PostgreSQL allows 80 connections; Redis uses a 128 MB data cap and rejects writes when full. Migrations run sequentially while application processes are stopped. CPU and memory limits are initial sizing, not measured capacity. Load tests on the actual A1 host must establish concurrent-user and voice-room limits before public enrollment.

This base budget excludes the upload scanner and object-storage process being evaluated under #244. Those services cannot be added by spending the same memory reserve twice. Remeasure the complete runtime under #244/#252 and reduce concurrency or revise the free architecture before enabling them.

Staging and production have separate Compose projects, volumes and secrets, but **only one is active on this VM**. Deploy commands reserve the host and reject a second environment. Normal staging runs on ephemeral standard GitHub-hosted runners; a staging hostname on the VM is an optional maintenance-time replacement for production, not an always-on second environment. Standard runners are free for this public repository; the workflow refuses private repositories. See [GitHub-hosted runner availability and billing](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).

## Build and release evidence

`infra/production/runtime-lock.json` pins multi-architecture upstream image digests. Application images contain the exact compiled commit, run as UID 10001, expose readiness, use a read-only root filesystem and allow 35 seconds for shutdown. The React bundle is served by Caddy. No JVM, Node build, dependency download or source checkout is required on the production host.

The `Release package` workflow validates both AMD64 and ARM64 on pull requests. It runs backend and frontend checks, builds images, scans all runtime images for high/critical vulnerabilities and secrets, then starts an empty ephemeral staging environment. It runs migrations twice, checks PostgreSQL-specific indexes, readiness, HTTPS routing, the frontend, anonymous auth bootstrap and rejected foreign origins. A scanner or staging failure blocks packaging; do not silence a finding merely to publish.

Manually dispatch the workflow from merged `main` only after the normal `CI` workflow is green at that exact commit. Only that job receives release-write permission. It creates a draft GitHub Release named `deploy-<40-character-commit>` with one archive and SHA-256 file per architecture. Review the exact build, scan and staging runs before publishing the draft. No production credentials enter GitHub Actions. Each archive must be smaller than 2 GB; GitHub documents a 2 GiB per-file release limit and no total release-size or bandwidth limit. See [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).

Each bundle contains `release.json`, `images.tar`, its checksum, pinned runtime metadata and the operator scripts. The manifest records immutable Docker image IDs, architecture, commit and schema compatibility epoch. Docker saves the content by ID; the host validates the archive and every ID before stopping ingress. Tags such as `latest` are rejected. Checksums establish download integrity; the trusted source is the reviewed GitHub release and exact-commit CI, not an independently signed artifact attestation.

For a local Linux build with Docker, Java 21, Maven and Node 24:

```sh
export SOURCE_DATE_EPOCH="$(git show -s --format=%ct HEAD)"
node --test scripts/deploy/*.test.mjs
scripts/java21.sh mvn -B -s backend/.mvn/settings.xml -f backend/pom.xml verify -Dproject.build.outputTimestamp="$SOURCE_DATE_EPOCH"
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
bash scripts/deploy/build-release.sh arm64
bash scripts/deploy/smoke-release.sh arm64
```

The smoke script owns and deletes only its ephemeral `chanter-staging` volumes. Run it on a disposable runner, never on the production VM. Do not copy `.cache`, local `.env` files or demo data into release assets.

## Provisioning checkpoint

`infra/production/oci` is the versioned plan: one fixed A1 shape, 100 GB preserved boot volume, one virtual network, SSH from one administrator IPv4 address, HTTPS/HTTP and LiveKit ports only. The OCI provider and lock file are pinned. Supply a verified official Ubuntu 24.04 ARM64 image OCID rather than looking up a moving latest image. Cloud-init installs Docker from its signed upstream repository and a version/checksum-pinned Node runtime; host package patches may move independently of the immutable application release.

After the owner creates the free account and confirms capacity, keep credentials in the operator's OCI profile. Copy `terraform.tfvars.example` to ignored `terraform.tfvars`, replace placeholders, and set `confirm_always_free_only = true` only after checking that no other resource consumes the allowance. Review a saved plan before any apply:

```sh
cd infra/production/oci
tofu init -lockfile=readonly
tofu validate
tofu plan -out=reviewed-plan.tfplan
# Apply only the reviewed account/resource plan within the authorized free scope.
tofu apply reviewed-plan.tfplan
```

Terraform state and saved plans contain infrastructure identifiers. Keep them private and out of Git or release assets. `prevent_destroy` and preserved boot storage protect against accidental replacement; they are not backups. VM recreation, state recovery, backup/restore proof and media durability belong to [#244](https://github.com/Vinosaamaa/chanter/issues/244) and remain release prerequisites.

Point an owner-controlled DNS A record at the VM, with no stale AAAA record. Allow TCP 80/443 for Caddy's public certificate issuance and HTTPS, TCP 7881 and UDP 7882 for LiveKit, and TCP 22 only from the reviewed administrator address. Confirm both cloud and host firewall behavior. Caddy obtains and renews public certificates; HTTP redirects to HTTPS. Its container listens on unprivileged ports 8080/8443, mapped to host 80/443. See [Caddy automatic HTTPS requirements](https://caddyserver.com/docs/automatic-https).

## Private runtime configuration

Log in again after cloud-init so Docker group membership applies. Download the reviewed ARM64 bundle and checksum from the published release into `/srv/chanter/releases`. Verify the checksum before extracting into a new, commit-named directory. Example placeholders below must be replaced:

```sh
cd /srv/chanter/releases
sha256sum --check chanter-COMMIT-arm64.tar.gz.sha256
mkdir COMMIT
tar -xzf chanter-COMMIT-arm64.tar.gz -C COMMIT
node COMMIT/scripts/deploy/host.mjs init /srv/chanter/production production app.owned-domain.example PUBLIC_IPV4
```

Initialization generates independent random database passwords, JWT signing material, an internal service credential, Redis and LiveKit credentials. Every service receives only its required credentials in a private mode-0600 file under a mode-0700 environment directory. The gateway receives no database, SMTP or internal service credential. Initialization refuses existing or partial state to avoid replacing live database passwords. Back up this private state through the separately reviewed recovery process.

Edit `/srv/chanter/production/runtime/auth-service.env` locally with the approved free SMTP account: `CHANTER_EMAIL_FROM`, `CHANTER_SMTP_HOST`, `CHANTER_SMTP_PORT`, `CHANTER_SMTP_USERNAME`, `CHANTER_SMTP_PASSWORD`, and `CHANTER_SMTP_TLS_MODE` (`starttls` or `implicit`). Blank values or missing credentials block deployment. Obtain a verified sending identity and test actual inbox delivery without enabling billing. An SMTP endpoint must be available without a payment-based upgrade; account selection and real verification/reset delivery remain an external checkpoint. Port 587 with verified TLS avoids the usual outbound port-25 restriction.

Files contain literal `KEY=value` lines, without shell quotes or interpolation. Compose **2.30 or newer** is required for `env_file: format: raw`; dollar signs and punctuation in provider passwords are preserved. Never source these files in a shell, print `docker inspect`, print an expanded `docker compose config`, enable shell tracing, or paste runtime logs containing personal data into an issue. Use `config --quiet` for validation. Docker administrators can read container environments; this is an operator trust boundary, not a secret vault. See [Docker's raw environment-file format](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/).

Optional Google OAuth credentials may be added only to the auth file after configuring the exact HTTPS callback `/oauth/callback/google` in the provider account. This frontend page exchanges the callback through the API. The deployment always requires email verification, disables local email sinks and sets both public base URL and the exact allowed browser origin to `https://<configured-hostname>`. The #242 Secure/HttpOnly/SameSite refresh cookie remains on the same origin; browser auth mutations require the validated Origin and `X-Chanter-CSRF: 1`. Session revocation blocks refresh immediately; already-issued access JWTs can remain valid for at most 15 minutes.

No external inference endpoint or paid model is enabled. The existing deterministic grounding and hashing embeddings remain available; provider-agnostic optional AI belongs to [#248](https://github.com/Vinosaamaa/chanter/issues/248). MinIO and Redpanda are absent because this runtime does not use them. Course files use the dedicated `resources` volume until #244 establishes the durable storage/recovery contract.

## Deploy, rollback and recovery

```sh
node /srv/chanter/releases/COMMIT/scripts/deploy/host.mjs deploy /srv/chanter/releases/COMMIT /srv/chanter/production
node /srv/chanter/releases/COMMIT/scripts/deploy/host.mjs verify app.owned-domain.example /srv/chanter/production
```

Deploy checks the host architecture, memory allocation, runtime files, image checksums and Compose configuration, then locks the host. It stops ingress and applications, starts PostgreSQL/Redis, runs each service's Flyway migration once against its owned database, starts applications sequentially, starts LiveKit and ingress, and verifies public TLS/API health. Flyway's database history, not a local marker, makes retries safe. Normal application containers disable automatic migrations. Record `current.json` only after public health passes, retaining the prior receipt as `previous.json`.

This procedure has downtime, including database migration and JVM startup. Existing connections and voice calls disconnect; clients reconnect after readiness returns. Keep the current and previous extracted bundles and images. Never prune them during the rollback window.

```sh
node /srv/chanter/releases/COMMIT/scripts/deploy/host.mjs rollback /srv/chanter/production
```

Rollback restarts the recorded previous application images against the existing data. It never reverses SQL, deletes volumes or silently restores an old database. It is allowed only when both releases have the same reviewed `schemaEpoch` and identical PostgreSQL/Redis image IDs. Increase `infra/production/release-policy.json`'s epoch whenever a migration removes compatibility with the preceding binary. #242 begins epoch 2 because old browser-token inserts are incompatible with its required session linkage. A changed epoch or persistence image requires a reviewed fix-forward or backup recovery procedure, not automatic rollback.

Every release review must inspect all migrations since the previous receipt and explicitly record whether the previous binary can read and write the new schema. An unchanged epoch is a reviewer assertion of that compatibility, not an automated schema proof. Include the migration diff, chosen epoch and a previous-binary smoke against the migrated staging database in the release evidence; do not approve automatic rollback from the integer alone.

A failed compatible deployment attempts to restore the previous release and rechecks public health; the command still exits unsuccessfully so the failure is visible. An incompatible failure, failed recovery or failed first installation requires operator intervention. Ingress stays stopped after public health failure. Logs report operation names and commit IDs, not credentials. A process crash may leave `.deploy-lock`; confirm no deployment process is running before removing that empty lock directory. Never delete the runtime state or data volumes to clear a lock.

To replace production temporarily with staging, first stop the active environment, then initialize and deploy staging with a separate hostname and private state:

```sh
node /srv/chanter/releases/COMMIT/scripts/deploy/host.mjs stop /srv/chanter/production
# Deploy the separately initialized /srv/chanter/staging environment during approved downtime.
```

`stop` retains volumes and can stop a partially failed first deployment. Do not use `docker compose down --volumes` on the VM. Routine health commands use the recorded generated Compose path, `docker compose ... ps`, and the public verifier. Monitor free disk, memory, restart counts, database health, SMTP queue failures and certificate expiry. Keep at least 20 GB free for the next bundle and rollback images; when disk or memory is exhausted, reduce use or pause enrollment. Scaling past the free allocation requires a new owner decision; it must not trigger paid provisioning.

## Edge and remaining release proof

The baseline exposes Caddy directly through owner-controlled DNS. It has no Cloudflare account, proxy or WAF configured. Caddy strips client-supplied identity/internal-service headers, sets forwarding metadata, disables API caching, caches fingerprinted assets, and adds CSP, HSTS, frame and referrer policies. API/auth/reset/verification URLs and cookies must never enter a shared cache. Browser production previews are not created automatically and never receive production secrets.

If a free Cloudflare account is later selected, review an auditable change before enabling the proxy: export DNS and rules, use Full (strict) TLS, bypass cache for all API and auth/token-bearing routes, preserve the SPA's no-cache policy, and verify free WAF rule availability. Authenticate or restrict origin access before claiming that Cloudflare is an enforced edge; direct-origin bypass remains possible otherwise. Preserve LiveKit's separate media-port reachability. No account-dependent edge control is marked implemented by this package.

Required proof before opening public enrollment: green dual-architecture image/scan/staging runs at the released commit; actual A1 resource and disk measurements; owned DNS/public certificate; real email verification and password reset; browser login/reload/logout and session replay/revocation; uploads surviving restart and tested #244 restore; two-client LiveKit connectivity; a forced bad-release health failure and successful compatible rollback; and the operator's reviewed zero-spend account/resource inventory. Keep #243 open until those receipts exist.
