# Private Course Resource operations

Issue [#244](https://github.com/Vinosaamaa/chanter/issues/244) adds durable object metadata, quarantine and scanning inside `media-service`. Deployment packaging and real account/HTTPS proof depend on #243. No provider account or bucket has been provisioned. The production release must explicitly select S3 storage; local storage is for development and migration.

## Provider and free limits

Use one **private Standard OCI Object Storage bucket** in the same unupgraded Always Free account, with a dedicated S3-compatible customer secret key and a policy limited to that bucket. Disable public access, versioning and replication; do not add lifecycle rules that create paid storage classes. Never upgrade, subscribe to a paid fallback, enable recharge, or enter a payment flow as a workaround.

[Oracle's current Always Free documentation](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) lists 20 GB of combined object storage and 50,000 API requests per month for free-only accounts; the trial's Standard allocation is 10 GB. This module therefore reserves at most **8,000,000,000 resource bytes** and **40,000 object-operation attempts per UTC month**, leaving separate account space and request headroom for backups and operators. Do not allocate more than 2 GB of backup data during the initial 10 GB trial allocation. Verify the actual account console before creating resources. Application limits do not control other clients or provider policy changes.

The configurable adapter uses [Oracle's S3 compatibility API](https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/s3compatibleapi.htm). R2-compatible endpoints remain possible, but [R2 bills usage above its included allowance](https://developers.cloudflare.com/r2/pricing/) and is not the default zero-cost path. There is no automatic provider fallback.

## Runtime configuration

Set these in the reviewed release's secret environment file, readable only by its operator and runtime. Do not put actual endpoints, access keys, bucket names or credentials in commits, tickets or logs.

| Variable | Production value or rule |
| --- | --- |
| `CHANTER_MEDIA_STORAGE_BACKEND` | `s3`; startup fails if required S3 configuration is absent |
| `CHANTER_S3_ENDPOINT` | Account-specific HTTPS S3 endpoint; no path, query or credentials in URL |
| `CHANTER_S3_REGION` | Actual bucket region |
| `CHANTER_S3_BUCKET` | Private resource bucket; provision separately |
| `CHANTER_S3_ACCESS_KEY`, `CHANTER_S3_SECRET_KEY` | Dedicated least-privilege customer secret key |
| `CHANTER_MEDIA_BYTE_LIMIT` | At most `8000000000`; lower it if account capacity is smaller |
| `CHANTER_MEDIA_REQUEST_LIMIT` | At most `40000`; counts every attempted PUT, GET, LIST and DELETE |
| `CHANTER_MEDIA_CLEANUP_REQUEST_RESERVE` | `4000`; only DELETE uses this protected allowance |
| `CHANTER_MEDIA_SPOOL_DIR` | Private writable directory; bounded upload/download files, no web mount |
| `COURSE_RESOURCE_STORAGE_DIR` | Existing local resource directory, retained during migration |
| `CHANTER_CLAMAV_HOST`, `CHANTER_CLAMAV_PORT` | Private scanner listener, never publicly exposed; port `3310` |
| `CHANTER_MEDIA_WORKER_ENABLED` | `true`; set `false` during recovery before reconciling backups |
| `CHANTER_MEDIA_MIGRATE_LEGACY` | `false` normally; enable only for the reviewed legacy import |

The adapter uses path-style addressing, immutable conditional PUT, content MD5 on transport, persisted SHA-256, no SDK retries, and bounded network deadlines. Each attempted call is charged before network I/O in its own committed transaction. An uncertain call never refunds the request counter. Scan GETs and reconciliation LISTs share the normal 36,000-operation allowance; only confirmed object DELETE calls can use the remaining 4,000. Once the total allowance is exhausted, cleanup waits for the next UTC month and all affected files remain inaccessible. Budget reads and reservations serialize on one database row; this is intentional for the small launch tier.

Public configuration never permits HTTP object storage. The integration test enables a separate property accepting HTTP only for literal loopback hosts. S3Mock is an emulator and cannot prove provider credentials, IAM policies, anonymous-access denial, region correctness or free-account limits.

Startup binds the database to a SHA-256 fingerprint of the backend and normalized endpoint plus bucket, or the canonical local root. Endpoint trailing slashes and default ports do not change the fingerprint. Credentials are excluded so keys can rotate. A changed namespace stops startup before any object request, including cleanup. The binding persists even when the bucket is empty. Never clear it to work around a startup failure.

## Upload and download contract

All routes require the existing access-token authorization and course permissions. Browser session issuance/refresh follows #242; downloads do not accept object URLs or a refresh cookie as authorization.

| Request | Result |
| --- | --- |
| `POST /api/v1/courses/{courseId}/course-resources` | Existing multipart fields, HTTP 202, existing resource fields plus `status` and `sha256` |
| Optional `Idempotency-Key` | UUID scoped to the uploader; identical course/title/name/type/AI approval/bytes returns the same resource and status; a changed payload returns 409 |
| Optional `X-Content-SHA256` | 64 hexadecimal characters; a mismatch returns 400 before storage |
| `GET /api/v1/courses/{courseId}/course-resources` | Instructors see pending and failed entries; learners see only `AVAILABLE` |
| `GET /api/v1/course-resources/{id}` | Poll metadata; learners cannot inspect pending or rejected entries |
| `GET /api/v1/course-resources/{id}/content` | Ownership-checked attachment, `nosniff`, `no-store`; 409 until available, 404 after deletion |
| `DELETE /api/v1/course-resources/{id}` | Instructor-only, idempotent HTTP 204; access stops immediately, cleanup runs durably |
| `GET /api/v1/courses/{courseId}/course-resources/usage` | Instructor-only course `reservedBytes` and `availableBytes` |

Public statuses are `PROCESSING`, `AVAILABLE`, `REJECTED` and `FAILED`. No storage key, endpoint, internal lease, scanner signature or provider error is exposed. Reusing an idempotency key never restarts a rejected or deleted upload. To intentionally submit new work after a permanent failure, use a new key. Scanner failures automatically retry up to five times with a 60-second delay. Failed nonlegacy objects retain their bytes for at least 24 hours before cleanup; the failure record and original key remain durable. Infected files are rejected and queued for cleanup immediately.

A clean file becomes `AVAILABLE` before AI indexing. Separate internal `ingestion_status` values (`NONE`, `PENDING`, `FAILED`, `COMPLETE`) track durable index work. Failed indexing retries every ten minutes without deleting the clean object or releasing its byte reservation. Deletion still takes priority over index completion. Richer user-facing ingestion status belongs to #246; the public upload/list shape remains unchanged here.

Accepted types are UTF-8 text/Markdown, PDF, PPTX, MP3, M4A, WAV, OGG, MP4, WebM and MOV, capped at 10 MiB of actual streamed bytes. Legacy binary `.ppt` must be converted to `.pptx`. The extension, declared MIME and detected content must agree. Filenames are normalized to safe basenames; titles and filenames have length/control-character checks. PDF trailers and bounded, nonmacro PPTX archive structure are checked before storage. These checks establish supported type, not perfect document validity; ClamAV supplies the malware gate.

Each download fetches once to a private file of at most 10 MiB, verifies length and SHA-256, checks that deletion has not won during the read, then streams it as an attachment. Unauthorized, corrupt, pending, rejected and deleted requests return no object bytes. An already authorized download that passed the final state check may finish while a later deletion is accepted. At most two foreground transfers run per service process; busy callers receive 429. Worker and spool cleanup do not publish files.

## Scanner and capacity

Run maintained ClamAV with a persistent signature directory, UTC timezone and FreshClam updates. Use the reviewed `infra/media-security/clamd.conf`: one-minute signature checks recover a startup notification race; encrypted or over-limit content produces a rejection; reloads block briefly instead of holding two engines. Scan uses the real INSTREAM protocol; missing, malformed, stale (older than 72 hours), or unavailable definitions fail closed. Socket deadlines bound both reads and writes. No local or production clean-verdict bypass exists.

[ClamAV's container instructions](https://docs.clamav.net/manual/Installing/Docker.html) recommend **4 GB** of RAM. The earlier #243 base caps total 7.625 GiB and exclude scanning. Adding 4 GiB would leave insufficient room for the OS on a 12 GB VM. Reallocate and measure the whole stack before deployment; passing the isolated media integration suite does not prove the full launch capacity. No extra VM or paid scan service is authorized as a fallback.

## Migration and recovery

1. Put resource uploads and AI resource retrieval behind maintenance before changing storage. Take an encrypted database backup and preserve the original local resource directory. Clear legacy AI resource chunks using the existing authenticated internal deletion route before allowing retrieval again; old chunks predate the quarantine guarantee.
2. Apply media Flyway V2 through the reviewed release migration command. Existing rows become `LEGACY`, retain their byte reservation and become unavailable for downloads. Confirm their sum fits the 8 GB resource allocation. Never rewrite V1 or mark legacy rows available manually.
3. Configure the private S3 bucket and scanner, mount original files, and set `CHANTER_MEDIA_MIGRATE_LEGACY=true`. The worker persists a new immutable object key before uploading, validates actual legacy size/type, verifies an uncertain PUT using a single GET, and then quarantines the copy for scanning. Original local files remain intact. A concurrent delete also cleans any reserved migration key.
4. Wait until expected legacy rows become `AVAILABLE` or investigate failures. Unsupported, missing or changed files remain unavailable and reserved; restore or correct their input under maintenance, then use an operator-reviewed retry. Compare downloaded hashes and learner/instructor permissions. Turn legacy migration off. Retain old files through the backup/rollback window; remove them only through a separately reviewed exact inventory.
5. Rollback to a release predating V2 is **not compatible**: the old server ignores quarantine and expects local storage. Treat #244 as a schema-epoch boundary in #243. Restore database and local resources together behind maintenance, or fix forward with the current lifecycle model. A database migration alone is not a safe rollback.

On database restore, stop uploads and workers first. Restore the metadata and counters together, then compare a private `resources/v1/` object inventory with reserved database rows. Preserve extra objects while deciding whether they are newer accepted writes or true orphans. Rebuild accurate byte reservations and monthly attempts conservatively before reopening writes; never reset an uncertain monthly request count to zero. The account's independent backup reserve must cover the recovery plan.

To migrate a bound namespace, stop every media instance and keep resource and AI access behind maintenance. Copy the exact reserved object and migration-key inventory without removing originals; validate every destination object's length and SHA-256 against the database. Reconcile failed and interrupted writes and account for copy attempts against both provider budgets. Have the operator review the inventory and destination before updating the singleton `media_storage_budget.storage_namespace` fingerprint and matching release configuration together. Preserve the original binding and object inventory for recovery. Start with workers disabled, verify authorized reads and anonymous denial, then resume workers. A backend change also requires a reviewed metadata migration; changing only the binding is insufficient. There is no automatic rebinding, copy or deletion path.

Ordinary reconciliation runs hourly, processes at most ten pages per run and resumes its cursor. It deletes only module-prefixed objects older than 24 hours that have no active byte reservation. Database-backed interrupted writes and rejected/deleted objects have their own retry leases. Reconciliation never scans backup prefixes. Expired private spool files are removed after one hour. Do not run reconciliation against a partially restored database.

## Verification and release proof

Run local module tests with `mvn -s backend/.mvn/settings.xml -f backend/pom.xml -pl media-service -am verify`. The isolated real-process suite is `.github/workflows/media-security.yml`: pinned PostgreSQL, Adobe S3Mock and ClamAV images on native AMD64 and ARM64, real EICAR rejection, immutable writes, metered attempts, and preserved-volume process restart. EICAR is the harmless standard antivirus test fixture.

For a local Docker host: `docker compose -f infra/media-security/compose.yml up -d`, run `python3 scripts/media/wait-dependencies.py`, then set `MEDIA_INTEGRATION=true` and `MEDIA_RESTART_PHASE=false` for `mvn ... test -Dtest=PrivateStorageIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`. Restart those three services, set `MEDIA_RESTART_PHASE=true`, and rerun the same test. The fixture uses test-only credentials, loopback ports and independent named volumes. Remove that exact test stack with its Compose `down -v` command when finished.

Keep #244 open until the merged #243 package has real private-provider IAM/anonymous-denial tests, upload/download/delete/recovery proof, a measured 2 OCPU/12 GB workload including the scanner, and browser evidence for processing and failure states. No provisioned provider or production proof is claimed by this repository change.
