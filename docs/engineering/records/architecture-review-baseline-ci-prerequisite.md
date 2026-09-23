---
schemaVersion: 1
id: architecture-review-baseline-ci-prerequisite
revision: 1
type: architecture-review
status: proposed
title: "Separate baseline audio evidence and Teaching consolidation"
repository: chanter
capabilityIds: ["chanter-ui-v2"]
createdAt: 2026-09-23
reconstructed: false
confidence: high
unknowns: ["Hosted pixels, product audio, full review and merged-main proof remain required."]
modules: ["frontend"]
interfaces: ["teaching-dashboard", "moderation-audio-test"]
seams: ["legacy-dashboard-to-teaching", "rtc-report-lifetime"]
adapters: ["react-router", "rtc-stats"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Teaching dashboard", "Moderation audio evidence"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Baseline CI prerequisite", "url":"https://github.com/Vinosaamaa/chanter/issues/344", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:TeachingPage", "test:use-instructor-dashboard-page", "test:moderation-audio-stats"]}
visibility: public-safe
publicationEligibility: eligible
issue: 344
pr: null
release: null
run: null
---
# Separate baseline audio evidence and Teaching consolidation

The larger UI integration depends on lifecycle/recovery verification. These two
corrections have independent value: retain real observed audio counters when RTP
reports disappear, and replace duplicated dashboard code with equivalent Teaching
controls. Both preserve existing API and budget boundaries.

The focused Teaching tests reproduced four failures before extraction and pass
afterward; audio tests cover removed reports, continued reception and identity.
Design and system review describe unchanged contracts and outstanding hosted
evidence. Synthetic UI fixtures are distinct from real product audio verification.
