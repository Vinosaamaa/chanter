---
schemaVersion: 1
id: feature-retrospective-chanter-ui-v2
revision: 1
type: feature-retrospective
status: released
title: Implement UI v2 course-first product shell (#116-#128)
repository: chanter
capabilityIds: ["chanter-ui-v2"]
createdAt: 2026-07-13
reconstructed: true
confidence: high
unknowns: ["Attachment bodies and workflow logs were not quoted.","A public launch was not evidenced; release truth remains strong local beta.","Sensitive source values and nonessential risky evidence links were omitted."]
modules: ["frontend"]
interfaces: ["frontend/src/features/v2-shell/v2-routes.ts","frontend/src/features/v2-shell/home/build-home-view-model.ts","frontend/src/features/v2-shell/pages/course/CourseOverviewPage.tsx"]
seams: ["signed-in-router-to-course-workspace","responsive-shell-to-feature-pages","view-models-to-presentation-components"]
adapters: ["frontend/src/features/v2-shell/v2-routes.ts","frontend/src/features/v2-shell/home/build-home-view-model.ts","frontend/src/features/v2-shell/pages/course/CourseOverviewPage.tsx"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Course-first signed-in navigation","Responsive learner and teaching surfaces","Unified public and authenticated visual direction"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Pull request #130","url":"https://github.com/Vinosaamaa/chanter/pull/130","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["pull-request:130","head-commit:d2eab5710ab73b2b280093a4634cb461be534403","merge-commit:2d26f6830b3f200e0bd8110cf88b12c2a3496300"]}
visibility: public-safe
publicationEligibility: eligible
issue: 116
pr: 130
release: null
run: null
---
# Implement UI v2 course-first product shell (#116-#128)

## Context

The earlier application shell followed a server-and-channel presentation that no longer matched the approved education-first product direction. Pull request #130 rebuilt the experience around Courses and the tasks learners, instructors, and owners perform within them.

## Delivered boundary

The change introduced a responsive signed-in shell and route families for Home, Inbox, Calendar, Course workspaces, Community, Teaching, billing, Friends and Direct Messages, and owner creation flows. It also refreshed authentication, onboarding, and the public landing experience to use the same visual system.

The PR combined thirteen UI slices and preserved legacy routes for compatibility. Later operationalization issues remained responsible for replacing presentation data and inactive controls with truthful service behavior.

## Verification

The PR records frontend lint, 67 tests across 21 files, a production build, multiple responsive viewport checks, product-stack health, and signed-in route smokes. The existing large-bundle warning was recorded as non-blocking rather than hidden.

## Consequences

Course became the dominant navigation and information architecture boundary. This made frontend composition clearer but also exposed a follow-on obligation: every polished route had to be connected to authorization-aware backend truth before public launch claims could be made.

## Historical limits

Private source details and machine-local evidence were omitted. The record describes the merged UI system and does not imply that Chanter was publicly launched.
