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
