# Issue #109 - debug log

## Cross-account Study Server cache leak

### Symptom

During the real two-user Chrome flow, signing out from the existing demo owner and signing in as a fresh Issue #109 owner changed the account identity, but the sidebar initially displayed Study Servers from the previous account.

### Investigation

1. Confirmed through gateway preflight that the fresh owner had exactly one new Study Server and one co-member.
2. Opened `/app/friends` and confirmed Friend Request queries were correct for the new user because their query keys already included `userId`.
3. Inspected `useAccessibleStudyServersQuery` and found the global key `['study-servers']`.
4. Inspected `V2Sidebar.signOut` and `auth-store.clearSession`; both removed tokens, but neither cleared the app-lifetime TanStack Query client.
5. Confirmed navigation queries used only `studyServerId`, even though the returned capabilities differ by viewer.

### Root cause

Authenticated server/navigation responses were cached under identity-independent keys, and the Query Client survived sign-out. A subsequent account could render the previous account's cached data until refetch completed.

### Fix

- Added `AuthenticatedQueryCacheBoundary` to clear all query data when the authenticated user ID changes or becomes null.
- Kept same-user token refreshes from clearing useful data.
- Added `userId` to Study Server and Study Server navigation query keys.
- Added regression tests for account change, sign-out, and same-user refresh behavior.

### Verification

Repeated the owner-to-peer account switch in Chrome after Vite hot reload. The peer immediately saw only `Issue 109 Shared Server`, the correct accepted Friend, and the persisted DM. No prior-account Study Servers appeared.

## Local Docker executable not found

### Symptom

The first `make product-supervise` attempt reported that `docker` was not found even though Docker Desktop was available.

### Root cause

The desktop task shell had a restricted `PATH` that omitted `/usr/local/bin` and `/opt/homebrew/bin`.

### Resolution

Product commands were run with:

```sh
PATH=/usr/local/bin:/opt/homebrew/bin:$PATH make product-supervise
```

No repository code change was needed. The stack then started and passed `make product-health`.
