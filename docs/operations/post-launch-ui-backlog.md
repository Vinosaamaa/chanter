# Post-Launch UI & product backlog

> **Purpose:** Items from the [#87 gap audit](public-launch-ui-gap-audit.md) that are **out of Public Launch scope** (#86–#104) or **stretch** work easy to forget after beta ships.  
> **GitHub:** [Epic #107](https://github.com/Vinosaamaa/chanter/issues/107) · [Project #6](https://github.com/users/Vinosaamaa/projects/6) · [Milestone: Post-Launch](https://github.com/Vinosaamaa/chanter/milestone/6)  
> **Do not start** until [Public Launch project #5](https://github.com/users/Vinosaamaa/projects/5) is winding down.

## When to use this doc

| Milestone state | Action |
|-----------------|--------|
| Still working #88–#104 | Read only — do not start here |
| Public Launch #5 mostly **Done** | Revise [epic #107](https://github.com/Vinosaamaa/chanter/issues/107); break PL items into stories on [project #6](https://github.com/users/Vinosaamaa/projects/6) |
| Planning commerce / growth | Pull items from §1 (PL-08 storefront) |

---

## 1. UI gaps deferred from Phase 1 (#88–#93)

Owner accepted **2026-07-09**. These were **Non-goals** on launch issues.

| ID | Gap | Mockup | Was non-goal on | Suggested slice |
|----|-----|--------|-----------------|-----------------|
| PL-01 | Resource **folder** hierarchy (flat list today) | `course-resources.png` | #88 | New story: resources folders + move/rename |
| PL-02 | Global search **message** results | `global-search.png` | #88 | Extend search index + UI filters |
| PL-03 | Global search **support-question** results | `global-search.png` | #88 | Same as PL-02 or dedicated |
| PL-04 | **Full** Plan & Billing settings page | `saas-billing.png` | #92 | New story: `/app/settings/billing` |
| PL-05 | Storage meter, invoices, upgrade CTA | `saas-billing.png` | #92 | With PL-04 |
| PL-06 | Landing notification / friends badges | `landing-page.png` | #104 | Marketing sprint |
| PL-07 | Sign-in invite / cohort-discovery **right pane** | `sign-in-onboarding.png` | #102 | Optional auth marketing |
| PL-08 | **Course storefront** | `course-storefront.png` | #104 | Commerce epic (new milestone) |

---

## 2. Stretch items (may ship incomplete at beta)

| ID | Gap | Mockup | Launch issue | If skipped at beta |
|----|-----|--------|--------------|-------------------|
| PL-09 | Friend **display names** | `friends-hub-dm.png` | #90 stretch | Profile lookup API + Friends Hub |
| PL-10 | TA queue / dashboard **display names** | `ta-queue.png`, `instructor-dashboard.png` | #92 stretch | Reuse profile lookup from PL-09 |

---

## 3. Cross-cutting cleanup

| ID | Item | Trigger |
|----|------|---------|
| PL-11 | Remove `/dev/demo` from production user paths | After #90, #91 merged; requires **#109** (send friend request UI) |
| PL-12 | Mobile: `#questions` context panel visible on small screens | Mobile UX pass |
| PL-13 | Mockup pixel-parity pass (all 19 PNGs) | Optional polish epic |

---

## 4. Not in gap audit but post-beta

| ID | Item | Notes |
|----|------|--------|
| PL-14 | i18n / accessibility audit | No issues in Public Launch |
| PL-15 | Performance / bundle budget | After staging (#101) |
| PL-16 | Admin / moderation tools | Product decision |

---

## GitHub tracking

| Asset | Link |
|-------|------|
| Epic | [#107 — Post-Launch product backlog](https://github.com/Vinosaamaa/chanter/issues/107) |
| Project board | [Post-Launch #6](https://github.com/users/Vinosaamaa/projects/6) |
| Repo Projects tab | [chanter/projects](https://github.com/Vinosaamaa/chanter/projects) |
| Milestone | [Post-Launch #6](https://github.com/Vinosaamaa/chanter/milestone/6) |

Created by `scripts/create-post-launch-tracking.sh` (2026-07-09). Re-run is idempotent once the epic exists.

**After #88–#104:** revise epic #107 + this doc, then add child stories to project #6.

---

## Related

- [Public Launch UI gap audit](public-launch-ui-gap-audit.md) — master mockup table
- [Public Launch issue breakdown](../issues/public-launch-issue-breakdown.md) — #86–#104
- [Public Launch project #5](https://github.com/users/Vinosaamaa/projects/5)
- [Post-Launch epic #107](https://github.com/Vinosaamaa/chanter/issues/107) · [project #6](https://github.com/users/Vinosaamaa/projects/6)
