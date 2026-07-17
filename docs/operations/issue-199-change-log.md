# Issue #199 Change Log — SEC-18: Restrict public actuator info

## Problem

Gateway treated all `/actuator/**` as public, so `/actuator/info` could leak build/git metadata unauthenticated.

## Changes

- Only `/actuator/health` (and `/actuator/health/**`) remain public.
- Other actuator paths (including `/info`) return `401` at the gateway filter.
- Gateway `management.endpoints.web.exposure.include` reduced to `health` only.

## Acceptance

- [x] `/actuator/info` not public
- [x] `/actuator/health` still works for probes
- [ ] CI + CodeAnt
- [ ] Browser / curl validation
