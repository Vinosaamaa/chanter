---
schemaVersion: 1
id: architecture-review-learning-desk-reconstruction
revision: 1
type: architecture-review
status: proposed
title: Reconstruct Chanter learning and community UI
repository: chanter
capabilityIds: ["responsive-learning-ui"]
createdAt: 2026-09-12
reconstructed: false
confidence: medium
unknowns: ["Backend-dependent interaction gates, manual screen-reader and actual browser-zoom verification remain pending"]
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
verification: {"state":"verified","evidenceRefs":["https://github.com/Vinosaamaa/chanter/actions/runs/34676290434","https://github.com/Vinosaamaa/chanter/actions/runs/34676290436"]}
visibility: public-safe
publicationEligibility: eligible
issue: 254
pr: 314
release: null
run: null
---
# Reconstruct Chanter learning and community UI

## Problem and decision

The owner rejected the existing product UI and requested a complete modern reconstruction with deliberate mobile layouts. A larger viewport previously expanded chrome and typography while phones inherited crowded desktop structures. The new system places Courses and conversations within a bounded reading canvas, with a quiet navigation index and a distinct Course-cover treatment.

The repository now vendors the upstream frontend-design skill at an immutable commit, including its license and invocation guidance. The versioned design plan records the palette, type, spacing, route families, mobile wireframes and pre-build critique before implementation.

## Boundaries

Presentation changes retain service-owned permissions, enrollment scope and authentication lifecycle. Role gates remain in the real components. Visual fixtures are reachable only through an explicitly selected test-server configuration; production build and startup do not import them. Screenshots from this server establish frontend layout, not persistence, email, authorization or backend capability.

## Implementation and verification

Home now prioritizes readable Course covers and a compact schedule, with a real retry and an actionable new-account state. Mobile Browse opens the real navigation, contains focus and restores it after Escape. Focused tests were observed failing before each new behavior and passing afterward. Local lint and production build passed for the initial shell/Home slice.

The required isolated Windows browser launch was denied by automatic approval review. A dedicated hosted Linux visual workflow captures synthetic frontend screenshots without traces, video, real credentials or private data. At commit cbe0c128, run 34676290434 passed all 179 fixture checks. It covers 24 route views at 360, 390, 768, 1280, 1920 and 3840px; phone list/detail/back behavior; portrait and landscape composers; tab scrolling; Calendar accessibility; native session dialogs; and seven Firefox plus seven WebKit checks. Actual images were inspected after the tests, including repaired phone Lounge, question author layout, tablet Teaching, sign-in and landscape sessions. Density-adjusted viewport checks establish equivalent reflow, not actual browser zoom or assistive-technology operation.

At the same commit, CI run 34676290436 passed 259 frontend tests, lint, dependency audits, production build and loading budgets, seven public browser journeys, and fourteen real signed-in journeys against the product services. The real journeys include registration, email verification, password recovery, reload/refresh/sign-out, account isolation and signed-in route loading. They do not establish every visible mutation, resource scanning, AI grounding or billing. This proposed record does not claim release or issue completion.


## Loading boundary

Route imports are deferred by the router, while protected-route ownership stays with authentication. The budget follows each route's static import graph, deduplicates shared files, and excludes unloaded dynamic imports. With secure sessions integrated, initial JavaScript is 108.7 KiB gzip, sign-in is 121.3 KiB, and signed-in Home is 137.3 KiB. Initial and Home caps are separately named at 120 KB and 150 KB gzip. The deferred voice client is capped at 130 KB; total raw JavaScript remains capped at 1.3 MB and gzip at 400 KB. Independent compression streams add about 43 KB to aggregate gzip while reducing initial loading.

## Open acceptance

Synthetic browser fixtures cannot establish authorization, realtime delivery, email, resource scanning, AI grounding, billing or durable sessions. Those checks require the corresponding merged services and real browser journeys. Manual screen-reader and actual browser-zoom verification, production-like Core Web Vitals, complete role/control inventory, and owner acceptance remain open. This review is proposed and makes no release claim.
