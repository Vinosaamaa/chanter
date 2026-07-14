# Issue #136 - CodeAnt fix log

**PR:** [#152](https://github.com/Vinosaamaa/chanter/pull/152)

| Pass | Finding | Resolution | Verification | Remaining threads |
|---:|---|---|---|---|
| 1 | `CreateEnrollmentRequest` accepted both `email` and `learnerUserId`; service precedence could enroll a different account than the UUID suggested. | Added request-level XOR validation so exactly one learner identity is required. Added a MockMvc regression for both ambiguous and empty payloads and verified no Enrollment is created. | `mvn -B -pl community-service -am -Dtest=CohortRosterSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test`; full Java reactor before push. | None after verification. |
| 2 | Internal directory endpoints exposed email-bearing profiles without service authentication; enrollment resolved email before manager authorization; a defensive error message described only one accepted identity. | Added constant-time shared-token authentication to both internal endpoints, sent the credential from the typed Community client, required manager access before any directory lookup, and corrected the defensive message. Added public-boundary regressions for missing/invalid credentials, client header propagation, and identical `403` responses for registered and unknown emails. | Focused Auth Service, HTTP client, and Cohort roster tests; full Java reactor before push. | None after verification. |

## Fix

```java
@JsonIgnore
@AssertTrue(message = "Provide exactly one learner identity")
public boolean isIdentitySelectionValid() {
    boolean hasEmail = email != null && !email.isBlank();
    return hasEmail != (learnerUserId != null);
}
```
