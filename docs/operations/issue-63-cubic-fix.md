# Issue #63 — cubic fix log

## Pass 1

| Comment | Action |
|---------|--------|
| P1: Study Assistant seed curls omit bearer token → 401 via gateway | Fixed — `Authorization: Bearer $OWNER_TOKEN` on presence, preview, install |
| P1: `questions` channel variable uninitialized in Python extractor | Fixed — `questions = ''` in init block |
| P2: Seed falls back to `servers[0]` when demo server missing | Fixed — return empty; create named server |
| P2: `ai-study-assistant.md` overstates re-seed when assistant already installed | Fixed — document clean-stack requirement for grants |
| P2: `localhost` vs `127.0.0.1` session mismatch in demo URLs | Fixed — default `FRONTEND` to `localhost`; note in workable-product-demo |
| P3: README Workable Product status stale (`#62–#32`, in progress) | Fixed — `#60–#63`, #31–#32 complete |
| P3: Published `DEMO_PASSWORD` in `.env.example` | Fixed — commented placeholder; script requires `DEMO_PASSWORD` in `.env` |
| P3: Change log “AI assistant” terminology | Fixed — “AI Study Assistant install” |
| P3: getting-started “Student” / “course files” glossary drift | Fixed — “Learner view”, “Course Resources” |
| P3: Dockerfile copies full `backend/` tree — broad cache invalidation | Fixed — copy module POMs + `common`/`realtime-service` sources only |
| P3: Demo checklist missing learner reply for chat/DM bidirectional proof | Fixed — added reply steps |
| P3: Unconditional Homework Help Guide upload on re-seed | Fixed — skip upload when title already exists |
