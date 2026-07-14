# Issue #138 - CodeAnt fix log

**PR:** [#154](https://github.com/Vinosaamaa/chanter/pull/154)

| Pass | Finding | Resolution | Verification | Remaining threads |
|---:|---|---|---|---|
| 1 | Critical: a valid invite could substitute for Study Server membership when joining an `OPEN` Cohort. | Enforced Study Server membership unconditionally for `OPEN`; invite validation remains exclusive to `INVITE_ONLY`. Added a public MockMvc regression proving an outsider with the real invite receives `403`, and made legacy invitation fixtures explicitly invite-only. | Red: expected `403`, received `204`. Green: focused discovery/enrollment tests passed 7/7 and the complete Java reactor passed on Java 21. | Awaiting CodeAnt re-review. |

## Fix

```java
case OPEN -> {
    boolean member = courseRepository.listAccessibleStudyServers(learnerUserId).stream()
            .anyMatch(server -> server.id().equals(joinDetails.studyServerId()));
    if (!member) {
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Open Cohort enrollment requires Study Server membership"
        );
    }
}
```

## Regression

```java
mockMvc.perform(post("/api/v1/cohorts/{cohortId}/join", openCourse.cohort().id())
                .with(asUser(outsiderUserId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("inviteCode", inviteCode))))
        .andExpect(status().isForbidden());
```
