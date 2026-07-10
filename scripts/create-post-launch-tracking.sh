#!/usr/bin/env bash
# One-shot: Post-Launch milestone, epic issue, GitHub project #6, repo link.
# Safe to re-run only when milestone has zero issues (guard below).
set -euo pipefail

REPO="Vinosaamaa/chanter"
OWNER="Vinosaamaa"
MILESTONE_TITLE="Post-Launch"
PROJECT_TITLE="Post-Launch"
EPIC_TITLE="Epic: Post-Launch product backlog"

gh label create post-launch --repo "$REPO" \
  --description "After Public Launch #86–#104; see post-launch-ui-backlog.md" \
  --color C5DEF5 2>/dev/null || true

MILESTONE_NUM=$(gh api "repos/$REPO/milestones" --jq ".[] | select(.title==\"$MILESTONE_TITLE\") | .number" | head -1)
if [[ -z "$MILESTONE_NUM" ]]; then
  MILESTONE_NUM=$(gh api "repos/$REPO/milestones" \
    -f title="$MILESTONE_TITLE" \
    -f description="UI polish, commerce, and product gaps after Public Launch milestone #5 (#88–#104)" \
    --jq .number)
  echo "Created milestone #$MILESTONE_NUM"
fi

EXISTING=$(gh issue list --repo "$REPO" --milestone "$MILESTONE_TITLE" --state all --limit 1 --json number --jq 'length')
if [[ "$EXISTING" != "0" ]]; then
  EPIC_NUM=$(gh issue list --repo "$REPO" --milestone "$MILESTONE_TITLE" --label epic --state all --limit 1 --json number --jq '.[0].number')
  echo "Post-Launch milestone already has issues; epic: #$EPIC_NUM" >&2
  PROJECT_NUM=$(gh project list --owner "$OWNER" --format json --jq ".projects[] | select(.title==\"$PROJECT_TITLE\") | .number" | head -1)
  echo "Project: https://github.com/users/$OWNER/projects/${PROJECT_NUM:-unknown}"
  exit 0
fi

cat > /tmp/post-launch-epic-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/82 (Public Launch epic — complete **#88–#104** first)

## Purpose

Future reference so deferred gap-audit items are not forgotten after public beta. **Do not start** until [Public Launch project #5](https://github.com/users/Vinosaamaa/projects/5) is winding down.

**Source of truth:** [`docs/operations/post-launch-ui-backlog.md`](https://github.com/Vinosaamaa/chanter/blob/main/docs/operations/post-launch-ui-backlog.md)

## When to revise this epic

After **#88–#104** merge, re-walk mockups and update the backlog doc + break PL items into child stories on this project.

## Backlog checklist (PL-01 … PL-16)

### Deferred from Phase 1 UI (#88–#93)

- [ ] **PL-01** Resource folder hierarchy — `course-resources.png` (was #88 non-goal)
- [ ] **PL-02** Global search message results — `global-search.png`
- [ ] **PL-03** Global search support-question results — `global-search.png`
- [ ] **PL-04** Full Plan & Billing settings page — `saas-billing.png`
- [ ] **PL-05** Storage meter, invoices, upgrade CTA — with PL-04
- [ ] **PL-06** Landing notification / friends badges — `landing-page.png`
- [ ] **PL-07** Sign-in invite / cohort-discovery pane — `sign-in-onboarding.png`
- [ ] **PL-08** Course storefront (commerce) — `course-storefront.png`

### Stretch leftovers from beta

- [ ] **PL-09** Friend display names — `friends-hub-dm.png` (#90 stretch)
- [ ] **PL-10** TA/dashboard display names — `ta-queue.png`, `instructor-dashboard.png`

### Cross-cutting

- [ ] **PL-11** Retire `/dev/demo` from production user paths
- [ ] **PL-12** Mobile `#questions` context panel
- [ ] **PL-13** Mockup pixel-parity pass (optional)

### Post-beta engineering

- [ ] **PL-14** i18n / accessibility audit
- [ ] **PL-15** Performance / bundle budget
- [ ] **PL-16** Admin / moderation tools (product decision)

## Non-goals (for this epic)

- Replacing Public Launch work (#88–#104) — finish that milestone first.
EOF

EPIC_URL=$(gh issue create --repo "$REPO" \
  --title "$EPIC_TITLE" \
  --body-file /tmp/post-launch-epic-body.md \
  --label epic --label post-launch --label ops \
  --milestone "$MILESTONE_TITLE")
EPIC_NUM="${EPIC_URL##*/}"
echo "Created epic #$EPIC_NUM"

PROJECT_JSON=$(gh project create --owner "$OWNER" --title "$PROJECT_TITLE" --format json)
PROJECT_NUM=$(echo "$PROJECT_JSON" | jq -r .number)
PROJECT_ID=$(echo "$PROJECT_JSON" | jq -r .id)
echo "Created project #$PROJECT_NUM"

gh project link "$PROJECT_NUM" --owner "$OWNER" --repo "$REPO"
echo "Linked project to $REPO"

gh project item-add "$PROJECT_NUM" --owner "$OWNER" \
  --url "https://github.com/$REPO/issues/$EPIC_NUM"
echo "Added epic to project board"

cat <<EOF

Post-Launch tracking ready:
  Epic:     https://github.com/$REPO/issues/$EPIC_NUM
  Project:  https://github.com/users/$OWNER/projects/$PROJECT_NUM
  Repo tab: https://github.com/$REPO/projects
  Milestone: $MILESTONE_TITLE (#$MILESTONE_NUM)
EOF
