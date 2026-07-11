# Issue #91 — cubic fix log

## Pass 1 (PR #111)

| Finding | Severity | Action |
|---------|----------|--------|
| Disabled fieldset checkboxes treated as focusable on open | P2 | **Fixed** — initial focus targets first enabled button |
| Escape closes dialog while install pending | P2 | **Fixed** — ignore Escape when `isInstalling` |
| Conflicting seed vs production install docs | P3 | **Fixed** — section 2 cross-references section F option A |
| Change log uses `server` instead of Study Server | P3 | **Fixed** |
| Grant lists use `label` direct children of `ul` | P3 | **Fixed** — wrap rows in `li` |
| Duplicate 403 branch in preview error handler | P3 | **Fixed** — single `studyAssistantInstallErrorMessage` call |

### Verification (pass 1)

```bash
(cd frontend && npm run test -- --run study-assistant)
(cd frontend && npm run lint && npm run build)
```

## Pass 2 (PR #111 — Ask AI 500 on PostgreSQL)

| Finding | Severity | Action |
|---------|----------|--------|
| `pg_advisory_xact_lock` mapped to `Long.class` — void return crashes Ask AI with 500 on product stack | P1 | **Fixed** — execute lock without reading void column (`query((rs, rowNum) -> true)`) |

### Verification (pass 2)

```bash
(cd backend && mvn -B -q -pl agent-service -am test -Dtest=AiQuotaSmokeTest)
(cd frontend && npm run test -- --run study-assistant)
(cd frontend && npm run lint && npm run build)
# product stack: learner Ask AI returns 200 (low confidence OK without pre-install resources)
```
