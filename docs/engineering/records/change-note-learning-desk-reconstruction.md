---
schemaVersion: 1
id: change-note-learning-desk-reconstruction
revision: 1
type: change-note
status: proposed
title: Reconstruct Chanter learning and community UI
repository: chanter
capabilityIds: ["responsive-learning-ui"]
createdAt: 2026-09-12
reconstructed: false
confidence: medium
unknowns: ["Complete route visual review and backend-dependent interaction gates remain pending"]
modules: ["frontend-shell", "course-workspace", "community-ui"]
interfaces: ["responsive-navigation", "learning-workflows"]
seams: ["frontend-api-capabilities"]
adapters: ["react", "browser"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Readable Course navigation", "Mobile learning and conversations"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue #254","url":"https://github.com/Vinosaamaa/chanter/issues/254","kind":"issue"}]
verification: {"state":"not-recorded","evidenceRefs":[]}
visibility: public-safe
publicationEligibility: eligible
issue: 254
pr: null
release: null
run: null
---
# Learning desk reconstruction

## Problem and decision

The owner rejected the existing product UI and requested a complete modern reconstruction with deliberate mobile layouts. A larger viewport previously expanded chrome and typography while phones inherited crowded desktop structures. The new system places Courses and conversations within a bounded reading canvas, with a quiet navigation index and a distinct Course-cover treatment.

The repository now vendors the upstream frontend-design skill at an immutable commit, including its license and invocation guidance. The versioned design plan records the palette, type, spacing, route families, mobile wireframes and pre-build critique before implementation.

## Boundaries

Presentation changes retain service-owned permissions, enrollment scope and authentication lifecycle. Role gates remain in the real components. Visual fixtures are reachable only through an explicitly selected test-server configuration; production build and startup do not import them. Screenshots from this server establish frontend layout, not persistence, email, authorization or backend capability.

## Initial implementation and verification

Home now prioritizes readable Course covers and a compact schedule, with a real retry and an actionable new-account state. Mobile Browse opens the real navigation, contains focus and restores it after Escape. Focused tests were observed failing before each new behavior and passing afterward. Local lint and production build passed for the initial shell/Home slice.

The required isolated Windows browser launch was denied by automatic approval review. A dedicated hosted Linux visual workflow captures synthetic frontend screenshots at the requested widths without traces, video, real credentials or private data. Screenshot acceptance and the remaining route reconstruction are pending. This proposed record does not claim release or issue completion.
