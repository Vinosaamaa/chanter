## Summary

- TODO

## Acceptance Criteria

- TODO

## Test Plan

- [ ] Unit tests
- [ ] Integration or contract tests
- [ ] Frontend tests
- [ ] Docker Compose smoke test
- [ ] Not applicable because:

## Architecture And Operations Notes

- Service boundaries changed: yes/no
- Database migrations added: yes/no
- Events or API contracts changed: yes/no
- Security or permission behavior changed: yes/no
- Observability impact: yes/no

## Engineering impact

Select exactly one. CI requires `docs/engineering/changes/pr-<this-pr-number>.md` and validates the selection against its canonical receipt. Run `node scripts/new-engineering-receipt.mjs --help` after opening a draft PR.

- [ ] None — reason: replace with a concrete reason
- [ ] Change Note
- [ ] ADR
- [ ] Architecture Review
- [ ] Feature Retrospective
- [ ] Postmortem
- [ ] Capability Dossier

## Checklist

- [ ] Scope is a vertical slice with a clear user or operator outcome.
- [ ] Backend permission checks are enforced by the service performing the protected action.
- [ ] No secrets or local-only credentials are committed.
- [ ] Docs or ADRs are updated for durable architecture/process decisions.
- [ ] The canonical Engineering receipt and any exact rich-record revisions are committed.
