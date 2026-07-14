# Issue #135 - debug log

## Purpose

Record meaningful implementation and verification failures encountered while operationalizing durable Office Hours, including how each failure was isolated.

## 1. Empty participant control bodies silently changed state

**Symptom:** `PATCH .../participants/me/hand` with `{}` returned HTTP 200 and treated the missing primitive boolean as `false`.

**Diagnosis:** A focused MockMvc test proved Jackson defaulted an absent primitive `boolean` to `false`; the controller had no validation boundary.

**Fix:** Changed the request fields to `@NotNull Boolean`, added `@Valid` at both controller endpoints, and kept service APIs primitive after validation.

**Proof:** `participantControlRequestsRequireExplicitBooleanValues` first failed with expected 400/actual 200, then passed for both hand and speaking requests.

## 2. Direct browser bridge could not initialize

**Symptom:** The desktop browser runtime failed before selecting the local app with `Cannot redefine property: process`, including after one clean runtime reset.

**Diagnosis:** Product health was green and the failure occurred before any request reached Chanter, isolating it to the desktop browser-control bridge rather than the app.

**Response:** Continued verification through the live gateway and PostgreSQL instead of changing application code for a tooling failure.

## 3. macOS was locked during visible browser verification

**Symptom:** The Accessibility-based Computer Use fallback reported that the Mac was locked and automatic unlock was unavailable.

**Diagnosis:** This happened before Chrome state could be read. The frontend, gateway, realtime service, and LiveKit health checks all remained green.

**Response:** Completed a two-user live API/token smoke and recorded visible audio/browser verification as pending rather than claiming it passed.

## 4. First live smoke helper lost `curl`

**Symptom:** The initial zsh smoke helper printed `auth: command not found: curl`.

**Diagnosis:** The function declared a local variable named `path`, which is zsh's special array tied to `PATH`; assigning an API route temporarily replaced command lookup paths.

**Fix:** Renamed the local variable to `api_path` and reran the same workflow.

**Proof:** The rerun completed all lifecycle assertions and printed listener/speaker token permissions plus an empty final roster.
