# Issue #57 — CodeRabbit fix log

## Pass 1 (PR #74)

Addressed sixteen inline comments from the first CodeRabbit review.

| Comment | Resolution |
|---------|------------|
| **Critical** — `GlobalSearchController`: reindex lacks instructor authorization | `GlobalSearchService.reindexStudyServer` requires `canViewFullCatalog`; learners receive HTTP 403 |
| **Critical** — `GlobalSearchService`: per-viewer reindex pollutes shared index | Search re-filters each hit against the viewer's visible resource/FAQ IDs from media + message services |
| **Major** — client property records lack bind-time validation | Added `@Validated`, `@NotBlank` on `baseUrl`, `@Positive` on timeout fields for all three client property records |
| **Major** — `HttpMediaCatalogClient`: downstream `401` collapsed to empty list | Removed `Unauthorized` swallow; only `403`/`404` return empty |
| **Major** — `GlobalSearchSmokeTest`: resource-level leakage untested | Added instructor-only resource case; learner reindex forbidden test |
| **Major** — `GlobalSearchOverlay`: stale async search responses | Cancel flag + reset state when query &lt; 2 chars |
| **Major** — `GlobalSearchOverlay`: focus escapes modal | Tab trap within overlay panel; restore focus on close |
| **Major** — `AppTopBar`: search hidden on small screens | Search button always visible; `⌘K` hint hidden below `sm` |
| **Minor** — `JdbcSearchIndexRepository`: LIKE wildcards not escaped | Escape `%`, `_`, `\` with `ESCAPE '\'` and `Locale.ROOT` |
| **Minor** — `use-global-search-shortcut`: `Ctrl/Cmd+K` toggles closed | Shortcut is open-only; `Escape` closes |
| **Minor** — `issue-57-change-log.md`: verification `cd` blocks | Use subshells `(cd …)` |
| **Minor** — `issue-57-coderabbit-fix.md`: placeholder | Replaced with this log |
| **Trivial** — duplicated `RestClient` construction | `DownstreamRestClientFactory` shared helper |
| **Trivial** — per-row JDBC inserts on reindex | `batchUpdate` with `BatchPreparedStatementSetter` |
| **Trivial** — `@MockBean` deprecated | Migrated smoke test to `@MockitoBean` |
| **Trivial** — shortcut tests missing `Ctrl+K`, `/`, editable guard | Added Vitest cases |

**Verification:**

```bash
(cd backend && mvn -B -pl search-service -am test)
(cd frontend && npm test && npm run lint && npm run build)
```

**Remaining threads:** none from pass 1.

## Pass 2

| Comment | Resolution |
|---------|------------|
| **Major** — `JdbcSearchIndexRepository`: delete+insert not transactional | Added `@Transactional` on `replaceStudyServerIndex` |
| **Minor** — seed script hardcoded demo password | Require `DEMO_PASSWORD` env var |
| **Minor** — seed script predictable `/tmp` path | Use `mktemp` + `trap` cleanup |
| **Minor** — `GlobalSearchOverlay`: effect deps use `trimmedQuery` | Updated dependency array |
| **Trivial** — `GlobalSearchService`: simplify `Collectors.toSet()` | Applied |
| **Trivial** — per-search downstream fan-out / TTL cache | **Deferred:** correct visibility filtering needs live checks in this slice; cache invalidation belongs with event-driven indexing (#57 deferred) |
| **Minor** — prefer `focus-trap-react` over hand-rolled trap | **Deferred:** manual Tab trap satisfies a11y for this overlay; no new dependency for bootstrap slice |

**Verification:** same as pass 1.

**Remaining threads:** TTL visibility cache (deferred with Kafka incremental indexing).
