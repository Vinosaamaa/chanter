# Owner cohort invite system review

The owning policy already permits the Study Server Owner and the Course
Instructor to manage cohort people. Navigation computes `canManagePeople` from
owner or instructor, and enrollment/TA services check `cohortHasPeopleManager`.
The new invite query follows those exact relationships. An owner role for another
server or an instructor role for another course cannot satisfy the predicate.
An enrolled learner or cohort TA is a roster viewer, not a people manager.

The authorization branches use EXISTS and return at most one cohort invite even
when the caller holds both roles. User, cohort and role parameters remain bound.
Reading the invite and checking authority happen in one SQL statement; no invite
is loaded by a separate unchecked read. The service's subsequent existence check
only selects between the existing 404 and 403 errors and returns no invite data.
No new cache, claim, token, route, migration or background work is introduced.

The regression fixture explicitly replaces the default course instructor with a
different identity. This catches the prior test blind spot where the creator was
both owner and instructor. Real database and service checks prove allowed and
denied paths; the existing enrollment suite covers the dual-role creator. Local
verification uses H2, while full hosted CI retains the existing PostgreSQL product
checks. No local product server or browser was started, and this change makes no
new pixel or production-cutover claim.

The predicate duplicates the existing small SQL policy rather than adding a
general authorization abstraction. If that policy changes, both owning queries
and their role matrix must change together. The adjacent People pagination,
legacy route redirects and frontend dialogs are explicitly outside this fix.
Full CodeAnt and exact-head CI remain review gates; no blocking source finding was
identified in the local diff review.
