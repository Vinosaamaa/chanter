# Issue #239 change log: Study Server membership navigation consistency

**Issue:** [#239](https://github.com/Vinosaamaa/chanter/issues/239)
**Branch:** `fix/239-study-server-membership-navigation`
**Date:** 2026-08-09

## Outcome

Made accepted Study Server membership the canonical authorization boundary for server-wide navigation. A generic member can now load Home, the sidebar, and Study Server Channels without receiving a 403, while Course and Cohort content remains restricted to Owners, Instructors, TAs, and enrolled Learners.

## TDD evidence

### Red

Extended the invitation lifecycle test through navigation, channel access, and Home before changing production code:

```text
JAVA_HOME=... mvn -pl community-service \
  -Dtest=StudyServerLifecycleSmokeTest test

Expected: 200
Actual:   403
Message:  Study Assistant presence requires Study Server membership or enrollment.
```

### Green

After the authorization fix, the same test passed all five lifecycle cases. The neighboring role and Home suites passed all ten cases, and the full community-service suite passed all 93 tests.

## Code changes

### Navigation uses canonical Study Server membership

`StudyServerNavigationService` now checks the same membership contract used by the accessible Study Server list:

```java
if (!studyServerRepository.isStudyServerMember(studyServerId, userId)) {
    throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Study Server navigation requires membership"
    );
}
```

Generic members receive an empty Course/Cohort scope rather than being rejected by the stricter Study Assistant scope:

```java
StudyAssistantViewerScope viewerScope = courseRepository.findViewerScope(studyServerId, userId)
        .orElseGet(() -> new StudyAssistantViewerScope(
                studyServerId,
                false,
                List.of(),
                List.of(),
                List.of()
        ));
```

This does not widen Study Assistant, Course, or Cohort authorization. Existing role and Enrollment filters still decide which scoped content is returned.

### Invitation regression covers the complete member journey

`StudyServerLifecycleSmokeTest` now proves that an invited user can accept membership, list the Study Server, load navigation, use the general channel, and load Home without seeing a Course they are not enrolled in:

```java
mockMvc.perform(get(
                "/api/v1/study-servers/{studyServerId}/navigation",
                created.id()
        ).with(asUser(inviteeUserId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studyServerChannels[?(@.name=='general')]").exists())
        .andExpect(jsonPath("$.courses.length()").value(0));

mockMvc.perform(get("/api/v1/me/home-summary").with(asUser(inviteeUserId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(0))
        .andExpect(jsonPath("$.partialFailures.length()").value(0));
```

### Signed-in browser regression rejects unexpected API failures

The learner product test now records every failing API response while signing in and loading Home/sidebar data:

```ts
page.on('response', (response) => {
  const url = new URL(response.url())
  if (url.pathname.startsWith('/api/') && response.status() >= 400) {
    unexpectedApiResponses.push(
      `${response.status()} ${response.request().method()} ${url.pathname}`,
    )
  }
})

expect(unexpectedApiResponses).toEqual([])
```

## Authorization matrix retained

| Principal | List server | Server channels | Course/Cohort scope |
|---|---:|---:|---:|
| Owner | Yes | Yes | Owned scope |
| Instructor | Yes | Yes | Assigned teaching scope |
| TA | Yes | Yes | Assigned support scope |
| Enrolled Learner | Yes | Yes | Enrollment scope |
| Generic Study Server Member | Yes | Yes | None |
| Stranger | No | No | None |

## Verification performed

```text
StudyServerLifecycleSmokeTest                         PASS (5 tests)
Lifecycle + navigation role matrix + Home summary   PASS (10 tests)
community-service test suite                         PASS (93 tests)
Java 21 mvn -q verify                                PASS (12 modules)
npm run lint                                         PASS
npm run test                                         PASS (65 files / 213 tests)
npm run build                                        PASS
make product-health                                  PASS
make product-demo-seed                               PASS
Signed-in learner system-Chrome regression           PASS (1 test)
Unexpected API responses during browser regression   0
```

The existing frontend large-chunk warning remains assigned to #241/#254. Deterministic Playwright browser provisioning remains assigned to #241; this issue's browser regression was verified with installed system Chrome.
