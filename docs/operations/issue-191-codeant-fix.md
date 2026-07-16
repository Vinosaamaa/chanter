# Issue #191 CodeAnt fix log

## Round 1 — Duplicate code (SCR) gate failed

**Finding:** ~35% duplicated code — identical `extractTokenFromSubprotocols` in gateway and realtime.

**Fix:** Extracted `com.chanter.common.auth.WebSocketJwtProtocols` (pure `List<String>` helper, no spring-web dependency). Gateway and realtime now call the shared helper. Added `WebSocketJwtProtocolsTest`.

**Status:** Remediated; awaiting re-scan after push.
