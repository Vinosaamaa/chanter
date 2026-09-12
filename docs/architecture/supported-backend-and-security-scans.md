# Supported backend and complete package scans

Issue #319 is the release prerequisite discovered by the first native image scan in #243. The existing backend used Spring Boot 3.4.1; the first application image contained 33 high or critical findings. A successful source diff review or unit test run did not establish safe packaged dependencies.

## Dependency decision

Use Spring Boot 4.0.8 with its compatible Spring Cloud 2025.1.3 train on Java 21. Spring Boot 3.5.16 is the last free 3.5 release and its open-source support has ended. A temporary move to that line would leave the launch without free maintenance. Boot 4.0 is a supported, smaller migration than changing the application to the newer 4.1 generation. The existing ten service boundaries are retained for this security repair.

Use the Boot dependency management for Spring, Jackson, database drivers, logging and reactive networking. Override Tomcat to 11.0.25: the managed 11.0.24 still triggers CVE-2026-65182, CVE-2026-65905 and CVE-2026-68525 in every MVC application package. Apache confirms the fixes in 11.0.25; the scanner's severity labels differ from Apache's, but all three are repaired without suppressions. Nimbus JOSE JWT is explicitly pinned to 10.9.1 because the parent does not manage it. H2 is test-only and pinned to 2.5.250: the managed 2.4.240 release invalidates a CHECK constraint when the connection which created it closes. The real outbox tests reproduced that upstream defect. Production remains PostgreSQL; no migration SQL is modified.

## Compatibility implementation

| Boundary | Change | Verification |
| --- | --- | --- |
| HTTP JSON | Add Boot's official Jackson 2 compatibility module through common; explicitly prefer Jackson 2 for imperative and reactive HTTP converters. | Existing request/response, UUID, date and authorization tests; signed-in browser journeys. |
| Gateway | Use the supported WebFlux Gateway starter and the `spring.cloud.gateway.server.webflux` route namespace. | Real gateway CORS tests and full product routing. |
| JDBC migrations | Replace bare Flyway core with the dedicated Boot Flyway starter while retaining the PostgreSQL adapter. | Existing migrations and durable-session/outbox tests; native deployment migration gate. |
| Framework health | Move health imports to the Boot 4 contributor package. | Actual email-delivery health assertions. |
| Tests | Use the specific MVC/WebFlux test starters, moved MVC annotations, MockitoBean, and explicit reactive test client configuration. | Complete service suite, retaining the same behavior assertions. |
| Manually created HTTP client | Decode the response body with the test's configured mapper instead of asking Jackson 3 to construct a Jackson 2 tree. | Real DM signaling and media-token test. |

Jackson 2 compatibility is a deliberate temporary boundary. Migrating application JSON types to Jackson 3 must be explicit, with saved-answer, provider protocol, dates and streaming contracts tested together. Do not remove the module or HTTP preferences as unrelated cleanup.

## Password compatibility

The current forms and API allow up to 128 characters. Updated BCrypt correctly rejects input beyond 72 bytes; the new test reproduces the resulting failure for long ASCII and Unicode passwords. New registrations and resets therefore use Spring Security's PBKDF2-HMAC-SHA256 encoder, a random 16-byte salt, 600,000 iterations and a 256-bit result. The stored `{pbkdf2-sha256-v1}` identifier fixes those parameters for subsequent verification. Work-factor changes require a new identifier.

The delegating encoder still verifies existing unprefixed BCrypt hashes and explicitly prefixed BCrypt hashes. No bulk password rewrite is possible or attempted. Missing-account verification uses a precomputed dummy hash with the current work factor. Legacy BCrypt credentials created with overlong passwords cannot recover the discarded suffix; those accounts must use password reset. New password hashes require this release's decoder, so rollback to the previous application after new registrations is unsafe and must be covered by the deployment schema-compatibility epoch.

## Complete scan gate

`scripts/security/backend-artifacts.mjs` reads every service from the Maven reactor, requires one packaged executable JAR per service, extracts it into a fresh ignored run directory and records its SHA-256. Trivy 0.74.0 scans the extracted package in `rootfs` mode for high/critical vulnerabilities and secrets. Source-oriented `filesystem` mode can inspect only Maven metadata; a zero-result metadata scan is never sufficient.

The verifier requires an identified package path for every JAR in `BOOT-INF/lib`. Missing Java indexes, incomplete analysis or a changed artifact fail the gate. Findings in one service do not stop the other services from being scanned. The final result fails if any service has missing coverage, a scan error, a relevant vulnerability, or a secret finding.

Raw scanner output remains in ignored local or ephemeral CI storage. The published summary contains only service names, artifact digests, library coverage counts, package versions, advisory IDs and secret rule IDs. It excludes secret matches and local paths. The complete native production image scan remains required in #243 because an application package scan does not inspect the base operating system, Redis, PostgreSQL, Caddy or LiveKit.

## System review and rollout

Highest-risk changes are gateway routing, JSON conversion, Flyway auto-configuration and password verification. Existing schemas and endpoint contracts remain intact, but successful unit tests alone are insufficient: all real signed-in journeys, both native architecture release scans, database startup, migration replay and HTTPS checks must pass. The password decoder change is incompatible with old code after a new hash is stored; the production package must advance its compatibility epoch before deployment.

The current source still has the larger launch constraints recorded by #244–#255: ingestion and retrieval quality, durable events, privacy and retention, operations, abuse limits and public provider verification. Updating dependencies does not complete those capabilities. Free hosting and model billing choices are unchanged, and no provider credentials are required for this repair.

## Sources

- [Spring Boot 4.0.8 release](https://spring.io/blog/2026/08/20/spring-boot-4-0-8-available-now/)
- [Spring Boot 3.5 end of free support](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
- [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Cloud compatibility](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions)
- [Jackson 2 compatibility configuration](https://docs.spring.io/spring-boot/4.0/reference/features/json.html)
- [H2 CHECK constraint regression](https://github.com/h2database/h2database/issues/4291)
- [Trivy Java artifact coverage](https://trivy.dev/docs/latest/coverage/language/java/)
- [Apache Tomcat 11 security fixes](https://tomcat.apache.org/security-11.html)
