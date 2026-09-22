---
schemaVersion: 1
id: architecture-review-product-interaction
revision: 1
type: architecture-review
status: proposed
title: Integrated product navigation and browser acceptance
repository: chanter
capabilityIds: ["production-deployment"]
createdAt: 2026-09-22
reconstructed: false
confidence: high
unknowns: ["Final lifecycle and recovery union", "Hosted cross-browser and visual acceptance", "Manual assistive technology and production performance"]
modules: ["frontend-friends", "frontend-inbox", "product-browser-tests"]
interfaces: ["keyboard-list-detail-navigation", "hosted-product-browser-gate"]
seams: ["visible-pane-to-keyboard-focus", "fixture-to-real-service-evidence"]
adapters: ["playwright"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Accessible list-detail navigation", "Cross-browser signed-in journeys"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Integrated product interaction review", "url":"https://github.com/Vinosaamaa/chanter/issues/339", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["frontend/src/features/v2-shell/pages/FriendsPage.test.tsx", "frontend/src/features/v2-shell/pages/InboxPage.test.tsx"]}
visibility: public-safe
publicationEligibility: eligible
issue: 339
pr: 340
release: null
run: null
---
# Integrated product navigation and browser acceptance

Friends and Inbox hid the list on phones while keyboard focus remained on its hidden row. Return navigation left focus on the hidden Back control. Focus now follows the visible reading heading and returns to the selected row, with a heading fallback when the last item disappears. The native Add friend dialog provides an inert background, platform focus handling and Escape cancellation, replacing duplicated document-level key handling. Existing role, relationship and notification services remain authoritative.

The real signed-in browser gate previously installed Chromium alone. Firefox and WebKit now run the same product tests against the actual hosted service stack, one worker at a time. Synthetic screenshots remain separate layout evidence. Authentication traces, screenshots and video remain disabled in real account tests. Nothing in this change configures a provider, relaxes authorization or declares public launch.

Design follows learning-desk-v3 and the repository's pinned frontend-design skill. Existing independent core, initial-route and capability bundle budgets remain unchanged. Focus behavior has red-to-green component regressions; final screenshot, hosted, review and combined-release acceptance remain open.

Teaching retains bookmarked Study Server selection, Refresh, dashboard metrics and lifetime free-beta usage while its legacy route becomes a query-preserving alias. Community event titles are keyboard controls; repeated editor/details forms use native modality and restore focus. Event edits preserve the original audience identifiers, correcting a request that always sent HUB. These changes have focused red-to-green regressions. Actual cross-browser acceptance remains open because the first expanded real-browser run exposed a plain-HTTP test origin incompatible with WebKit Secure-cookie persistence.
