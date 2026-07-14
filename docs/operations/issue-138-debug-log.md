# Issue #138 - debugging log

## Purpose

Record failures encountered while operationalizing Community Course discovery, how they were isolated, and the permanent checks that prevent recurrence.

## 1. Frontend page tests leaked DOM between cases

**Symptom**

Later tests found multiple location probes, headings, and actions from earlier renders.

**Diagnosis**

This repository does not install automatic Testing Library cleanup globally. The new test file omitted the established local `afterEach(cleanup)` pattern.

**Fix**

Imported `cleanup` and registered it after every test. All five page tests then ran independently.

## 2. React lint rejected ref writes during render

**Symptom**

`npm run lint` rejected assignments to `closeRef.current` and `busyRef.current` in the dialog hook.

**Diagnosis**

The current React lint rules prohibit reading or writing refs during render, even when they are only used by later event handlers.

**Fix**

Moved both assignments into a dependency-tracked effect. Keyboard behavior remained green and full lint passed.

## 3. First migration draft weakened existing enrollment policy

**Symptom**

The first V14 draft backfilled every existing Cohort as `OPEN`.

**Diagnosis**

Before #138, joining always required an invite code. Backfilling `OPEN` would silently broaden access for deployed Courses even though new Course creation needs an open discovery path.

**Red test**

The V11 upgrade fixture expected its legacy Cohort to become `INVITE_ONLY` and a post-V14 Cohort to default to `OPEN`; it initially received `OPEN` for the legacy row.

**Fix**

V14 adds the column with temporary `INVITE_ONLY`, backfills and makes it non-null, then changes only the future default to `OPEN`. H2 and disposable PostgreSQL 16 both pass this distinction.

## 4. Maven was launched from the repository root

**Symptom**

The first runtime rebuild reported:

```text
Could not find the selected project in the reactor: community-service
```

**Diagnosis**

The Maven reactor root is `backend/`, not the repository root. The database backup had completed, but the command stopped before any process or schema change.

**Fix**

Ran the package command from `backend/`. The Community jar built successfully.

## 5. Bash product helpers were sourced by zsh

**Symptom**

The helper failed with:

```text
product_repo_root: BASH_SOURCE[0]: parameter not set
```

**Diagnosis**

`scripts/product/lib.sh` is a Bash library and uses `BASH_SOURCE`; the desktop command shell was zsh. The command again failed before stopping Community.

**Fix**

Invoked the helper under `/bin/bash`. It restarted only Community, applied V14, and returned healthy.

## 6. Browser runtime paths were not on the shell PATH

**Symptom**

The smoke runner first could not find `node`, then could not resolve `playwright`.

**Diagnosis**

Playwright is provided by the Codex desktop runtime rather than the frontend dependency graph, and that bundled runtime was not exported in the one-shot shell.

**Fix**

Loaded the workspace dependency paths and invoked the bundled Node executable with the bundled Playwright module path. No application dependency was added.

## 7. Browser selectors matched two accessible controls

**Symptom**

Playwright strict mode found both the page's Create Course trigger and the dialog submit, then later matched the Invite Code textbox and its close button.

**Diagnosis**

The controls intentionally share related accessible names; the runner's selectors were too broad.

**Fix**

Scoped Create Course submission to its dialog and selected Invite Code by textbox role. The resumable browser smoke then passed all journeys.

## 8. Persistent PostgreSQL upgrade was protected before migration

The normal Community database was at V13. It was dumped to `.product/backups/chanter-community-pre-v14-20260714-070622.sql` before restart. V14 then applied successfully, and a direct query confirmed all 45 legacy Cohorts remained `INVITE_ONLY`.

## 9. CodeAnt found an open-enrollment authorization bypass

**Symptom**

An authenticated outsider could submit the valid invite code attached to an `OPEN` Cohort and receive `204`, even without Study Server membership.

**Diagnosis**

The open-policy condition rejected only `!member && !validInvite`. That made an invite code a substitute for the documented Study Server membership boundary, even though invite authorization belongs only to `INVITE_ONLY` Cohorts.

**Red test**

`outsiderCannotUseAnOpenCohortInviteAsStudyServerMembership` exercised the public join endpoint and initially received `204` instead of `403`.

**Fix**

The `OPEN` branch now rejects every non-member regardless of invite validity. Invite matching remains scoped to `INVITE_ONLY`. The focused discovery suite passes all four tests on Java 21.

## 10. A legacy enrollment test depended on the removed bypass

**Symptom**

The first full reactor run failed `CourseEnrollmentSmokeTest` because its invite-driven join expected `204` from a newly created `OPEN` Cohort and now correctly received `403`.

**Diagnosis**

The test predates explicit enrollment policies. Its behavior was an invite-only journey, but its fixture inherited the new `OPEN` default and therefore accidentally depended on the authorization bypass.

**Fix**

The two invitation-specific test fixtures now explicitly use `INVITE_ONLY`. Their invite success/failure behavior remains covered, the new OPEN boundary remains strict, the focused seven discovery/enrollment tests pass, and the complete Java reactor passes on Java 21.

## Final proof

```text
owner and learner signed in through the real auth UI
owner created a persisted open Course and the truthful catalog count updated
learner discovered, filtered, and joined an open Cohort without an invite
enrollment invalidated navigation and catalog caches
learner joined an invite-only Cohort with a valid code
outsider with a leaked open-Cohort invite received 403
390x844 discovery rendered without horizontal overflow
consoleErrors: []
responseErrors: []
```
