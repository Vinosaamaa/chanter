# Issue #141 — Operational Community announcements, members, and invitations

## Summary

Made Community hub Announcements, Members, and Invite people truthful against durable Study Server APIs. Removed mock announcement/member fixtures and static hub chrome counts.

## Backend

- Migration `V17__community_announcements_and_members.sql`: `community_announcements` + `community_announcement_reactions` (LIKE).
- Announcement CRUD + archive + idempotent LIKE reaction under `/api/v1/study-servers/{id}/announcements`.
- Owner-only publish/edit/archive; members can list/react.
- Study Server member directory from existing membership union (`findMembers` / `countMembers`) with role priority and profile enrichment.
- `GET .../members`, `GET .../member-summary`, `POST .../invitations` (post-create invites reusing #139 invitation model + Home accept).

## Frontend

- Wired Announcements page to real APIs (publish/edit/archive/like); removed Sign up / comments / overflow dead controls.
- Upcoming events aside uses real #140 events + link to Events tab.
- Members page: search/filter/pagination, friend-gated Message deep link, Online/Active disabled until presence exists.
- Hub chrome: real member count + preview avatars; Invite people modal creates durable invites.

## Tests

- `CommunityAnnouncementSmokeTest` covers announcements + members + post-create invites.
- Frontend API unit tests for announcements and members clients; hub layout asserts real member count.
