# Issue #53 CodeRabbit Fix Log

PR: [#70](https://github.com/Vinosaamaa/chanter/pull/70)

## Pass 1 (`pending`)

| Comment | Fix |
|---------|-----|
| Client-supplied `viewerUserId`/`uploaderUserId` spoofable | `CourseResourceController` derives caller from `@RequestAttribute(USER_ID)`; media `AuthenticatedUserFilter` on `/course-resources`; frontend API stops sending identity query/body fields; smoke tests use `AuthHeaders.USER_ID` |
| `aiApproved` defaults to `true` | Upload checkbox defaults to unchecked (`false`) |
| Preview button downloads non-PDFs | Preview button only rendered for PDF resources |
| Download anchor not in DOM / early revoke | Append anchor to `document.body`, remove after click, defer `revokeObjectURL` |
| `apiFetchBlob` duplicates auth refresh logic | Extract shared `fetchWithAuth` + `parseJsonResponse` in `api-client.ts` |
