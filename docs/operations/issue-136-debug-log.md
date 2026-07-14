# Issue #136 - debug log

## Purpose

Record meaningful implementation and verification failures encountered while operationalizing the Cohort roster, Enrollment, invitations, and TA assignments.

## 1. First focused Maven selector used the wrong reactor root

**Symptom:** The initial focused backend command could not select the expected Community Service module.

**Diagnosis:** The command was launched with a module selector that assumed a different Maven working directory than this repository's backend reactor.

**Response:** Ran the focused test from the backend reactor with the correct module coordinates, then retained the root full-reactor command as the final gate. No production code changed for this command error.

## 2. Community Service failed to start after the Auth client was added

**Symptom:** Community Service never opened port 8082 during the first live product restart, which would surface as a Gateway `502` for Community routes.

**Diagnosis:** `HttpAuthUserDirectoryClient` had a public production constructor and a package-private test constructor. Spring could not choose a constructor and reported that no default constructor existed.

**Fix:** Added a failing production-construction regression test and marked the properties-based production constructor with `@Autowired`.

**Proof:** The regression changed from a constructor failure to green, Community Service started, and `make product-health` passed.

```java
@Autowired
public HttpAuthUserDirectoryClient(
        AuthServiceClientProperties properties
) {
    // production client setup
}
```

## 3. Desktop browser bridge could not initialize

**Symptom:** The desktop browser-control runtime failed before selecting Chanter with `Cannot redefine property: process`.

**Diagnosis:** Product health remained green and the failure occurred before any application interaction, isolating it to the browser-control runtime.

**Response:** Launched a separate local headless Chrome instance and controlled it through CDP for signed-in DOM assertions, responsive screenshots, and owner/learner journeys. No application workaround was added.

## 4. First repeated CDP sign-in lacked useful exception detail

**Symptom:** One rerun stopped at form submission with a generic CDP `Uncaught` error.

**Diagnosis:** The smoke helper returned only `exceptionDetails.text`, hiding Chrome's exception description. Services remained healthy and a fresh rerun reached the same sign-in page.

**Response:** Improved the ignored local smoke helper to report `exception.description` when present. The next run completed successfully without an application change.

## 5. First 390px capture cropped the desktop layout

**Symptom:** The initial mobile screenshot showed the sidebar covering most of a 390px image.

**Diagnosis:** CDP device metrics changed after the desktop page had initialized; the page had not reloaded under the mobile viewport, so the capture was not evidence of the responsive breakpoint.

**Fix:** Reloaded the People route after switching metrics, waited for the real roster, and recaptured.

**Proof:** The new 390x844 screenshot shows the mobile top bar, horizontal Course tabs, stacked controls, responsive roster cards, and no horizontal overlap.

## 6. Visual QA found shared Course header fixture data

**Symptom:** The first otherwise-correct owner screenshot still displayed `Dr. Alex Johnson` beside the real Cohort name.

**Diagnosis:** People content used the real roster, but `V2CourseChrome` still owned an old mockup-only instructor string. The first DOM assertion searched only `.people-page`, so it did not cover the shared header.

**Fix:** Added a shared-layout test, reused the Cohort roster query in the Course chrome, rendered the canonical instructor, widened the browser assertion to the workspace, removed the fake `2 live` state, and changed Invite into real People navigation.

**Proof:** Focused layout/People tests pass. Browser output reports `Professor Rowan`, `hasFixtureInstructor=false`, and `hasFabricatedPresence=false`.

## 7. Pre-commit review found an unhandled TA-removal failure

**Symptom:** If the remove-TA request rejected, the click handler produced an unhandled promise and showed no error to the manager.

**Diagnosis:** Add TA, assignment, enrollment removal, and invitation cancellation used guarded handlers, but `PersonRow` called the remove mutation directly.

**Fix:** Added a failing component test, moved removal into an awaited page handler, and report the formatted API failure through the existing People alert.

**Proof:** The test first failed with no `alert` plus an unhandled rejection, then passed with the service failure rendered to the manager; focused lint is clean.
