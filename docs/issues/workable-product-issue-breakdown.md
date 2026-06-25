# Chanter Workable Product Issue Breakdown

> **Goal:** A **local, clickable, full-stack app** — sign in, join channels, chat live, message friends, join voice rooms — not just mockup-aligned screens or API demo forms.
>
> **Depends on:** [Production Frontend](https://github.com/Vinosaamaa/chanter/milestone/3) (**#48–#59**) for the real UI shell and live **text** chat in Course Channels.

## GitHub milestone

**[Workable Product](https://github.com/Vinosaamaa/chanter/milestone/4)**

## GitHub project

**[Workable Product](https://github.com/users/Vinosaamaa/projects/4)**

**Board order = implementation order:** #60 → #30 → #62 → #61 → #31 → #32 → #63. See [`agent-roadmap.md`](agent-roadmap.md) § Phase 3.

## Epic

| # | Title |
|---|---|
| [60](https://github.com/Vinosaamaa/chanter/issues/60) | Epic: Workable Local Product (Full Stack) |

## Vertical slices

| # | Title | Blocked by | Delivers |
|---|---|---|---|
| [30](https://github.com/Vinosaamaa/chanter/issues/30) | Wire Auth Service Principal | #48 | Backend sessions (pair with #49) |
| [31](https://github.com/Vinosaamaa/chanter/issues/31) | Friends Hub + live DM | #51, #30, #50 | Talk to friends in UI |
| [32](https://github.com/Vinosaamaa/chanter/issues/32) | DM voice call | #31, #61 | 1:1 friend audio |
| [61](https://github.com/Vinosaamaa/chanter/issues/61) | Voice Channel WebRTC + LiveKit local | #51 | Hear/speak in voice channels |
| [62](https://github.com/Vinosaamaa/chanter/issues/62) | One-command local product stack | #48 (overlap ok) | `make product-up` style workflow |
| [63](https://github.com/Vinosaamaa/chanter/issues/63) | Workable product E2E demo path | #31, #61, #62 | Full click-through checklist |

## What Production Frontend does *not* deliver

| You want | Frontend milestone | Workable Product |
|----------|-------------------|------------------|
| Sign in (no UUID forms) | #49 + #30 | — |
| Discord-like shell | #50 | — |
| Live text in channels | #51 | — |
| Talk to friends (live DM) | stub only | **#31** |
| Friend voice call | — | **#32** |
| Join voice channel and speak | presence only | **#61** |
| One command → open browser | partial | **#62** |
| Proof everything works | per-slice | **#63** |

## Recommended order

Matches [project #4](https://github.com/users/Vinosaamaa/projects/4) board order — see [`agent-roadmap.md`](agent-roadmap.md) § Phase 3:

```
Production Frontend (prerequisite): #48 → #49+#30 → #50 → #51 → …

Workable Product (after #51 merges):
  #60 epic
  #30 (if auth not finished in phase 2)
  #62 One-command stack (may start early)
  #61 Voice WebRTC + LiveKit
  #31 Friends Hub
  #32 DM voice
  #63 E2E capstone
```

## “Workable app” acceptance checklist

1. Start stack with one documented command (**#62**).
2. Sign in as two users (**#30**, **#49**).
3. Live text chat in a shared channel (**#51**).
4. Friend request → accept → live DM (**#31**).
5. Join Voice Channel with audio (**#61**).
6. Optional: DM voice call (**#32**).

## Related docs

- [`docs/issues/agent-roadmap.md`](agent-roadmap.md) — **mandatory issue order**
- [`docs/issues/production-frontend-issue-breakdown.md`](production-frontend-issue-breakdown.md)
- [`docs/architecture/social-hub-and-dm-voice.md`](../architecture/social-hub-and-dm-voice.md)
