# Issue 12 Greptile Fix Log: Create A Study Server

Date: 2026-06-17  
Branch: `feature/12-create-study-server`  
PR: https://github.com/Vinosaamaa/chanter/pull/26  
Final Greptile confidence: `5/5`

## Summary

Greptile reviewed the Study Server creation slice and raised two P1 correctness issues plus one security placeholder. The correctness issues were fixed before merge. The auth placeholder was recorded as an explicit follow-up because authenticated principal wiring belongs to a later auth slice.

## Fixes Applied

### 1. Store Creation Time As An Absolute Timestamp

Greptile finding:

- `created_at TIMESTAMP NOT NULL` maps to `TIMESTAMP WITHOUT TIME ZONE` in PostgreSQL.
- Combined with `java.sql.Timestamp`, JVM timezone changes could permanently misinterpret persisted creation times.

Fix:

- Changed the migration to `TIMESTAMP WITH TIME ZONE`.
- Updated JDBC writes/reads to use `OffsetDateTime`.

Representative SQL snippet:

```sql
created_at TIMESTAMP WITH TIME ZONE NOT NULL
```

Representative Java snippet:

```java
.param("createdAt", OffsetDateTime.ofInstant(studyServer.createdAt(), ZoneOffset.UTC))
```

### 2. Read Study Server Aggregate In A Read-Only Transaction

Greptile finding:

- `findById` reads Study Server, Owner role, and channels through three queries without an explicit transaction.
- A future delete path could race between those reads and surface as a 500.

Fix:

- Added `@Transactional(readOnly = true)` to the repository read path.

Representative snippet:

```java
@Override
@Transactional(readOnly = true)
public Optional<StudyServer> findById(UUID id) {
}
```

### 3. Caller-Supplied Owner ID Documented As Temporary

Greptile finding:

- `CreateStudyServerRequest` accepts arbitrary `ownerUserId`.
- Any client can currently claim any UUID until auth integration exists.

Fix:

- Recorded the security constraint in code as a follow-up for the auth slice.
- The future auth implementation must replace caller-supplied `ownerUserId` with the authenticated principal from Gateway/security context.

Representative snippet:

```java
// TODO(#auth): replace caller-supplied owner ids with the authenticated principal.
```

## Final Result

- Greptile final summary: `5/5`
- Greptile final status: safe to merge
- PR #26 merged on `main`
