# Workable product demo (local)

This is the **definition of a workable local Chanter product**: cold start on a clean clone, one-command stack, two signed-in users, live course-channel text chat, friends + live DM, voice channel audio, and optional DM voice (#32).

**New to Chanter?** Read [`getting-started.md`](getting-started.md) first — install, start, and feature-by-feature clicks. **This doc** is the shorter **two-user proof checklist** once the stack is running.

**Issue:** [#63](https://github.com/Vinosaamaa/chanter/issues/63)

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Node | 20+ |
| Maven | 3.9+ |
| Docker | Running (Postgres, Redis, Redpanda, MinIO, realtime-service, LiveKit) |

Allow microphone access in the browser when joining voice.

---

## 1. Cold start

```bash
git clone https://github.com/Vinosaamaa/chanter.git
cd chanter
cp .env.example .env
make product-up
make product-health
```

`make product-up` starts infrastructure, builds and runs all backend services, starts the Vite frontend, and brings up **realtime-service** (Docker) and **LiveKit** for WebRTC.

Open **http://localhost:5173** — the app proxies `/api` to the gateway at **http://localhost:8080**.

Use the **same host** (`localhost`, not `127.0.0.1`) when opening seed-script URLs so your sign-in session carries over.

---

## 2. Seed demo data

The seed script registers two demo personas, creates a Study Server + course, enrolls the learner, accepts a friend request, **uploads an AI-approved resource**, and **installs the AI Study Assistant**.

```bash
DEMO_PASSWORD=chanter-dev-demo ./scripts/seed-workable-product-demo.sh
```

The script prints direct URLs. Default personas:

| Role | Email | Password |
|------|-------|----------|
| Owner (instructor) | `dev-demo-owner@chanter.local` | `chanter-dev-demo` |
| Learner | `dev-demo-learner@chanter.local` | `chanter-dev-demo` |

---

## 3. End-to-end checklist

Use **two browser profiles** (or one normal + one private window) so each user keeps a separate session.

### A. Sign in

1. Window **Owner** → http://localhost:5173/sign-in → sign in as `dev-demo-owner@chanter.local`.
2. Window **Learner** → same URL → sign in as `dev-demo-learner@chanter.local`.

Both should land in the app shell (`/app/...`).

### B. Live text in a course channel (#51)

1. In **Owner**, open the seeded **#announcements** course channel (URL from the seed script, or pick it from the sidebar under the demo course).
2. In **Learner**, open the same channel.
3. **Owner** types a message and sends.
4. **Learner** sees the message appear without refresh (realtime WebSocket).
5. **Learner** replies; **Owner** sees the reply live without refresh.

Pass: bidirectional live text in the shared course channel.

### C. Friends + live DM (#31)

1. **Owner** → top bar **Friends** → `/app/friends`.
2. Confirm **Demo Learner** appears in the friends list (seed script created the friendship).
3. Select the learner, send a DM.
4. **Learner** → **Friends** → select owner → message appears live.
5. **Learner** replies; **Owner** sees the reply live.

Pass: DM delivers in real time in both directions.

### D. Voice channel audio (#61)

1. Both users open the **study-room** voice channel (`study-channels/...` from seed output, or **study-room** in the Study Server sidebar).
2. **Owner** clicks **Join voice** → allow microphone.
3. **Learner** clicks **Join voice** → allow microphone.
4. Confirm **Connected members** lists both users; speak and verify audio (headphones recommended to avoid feedback).

Pass: both users connected; presence shows two members; audio works.

### E. Optional — DM voice call (#32)

1. **Owner** → **Friends** → select learner → **Call**.
2. **Learner** sees incoming call → **Accept**.
3. Confirm call UI shows connected state; speak and verify audio.
4. Either side **Hang up**.

Pass: 1:1 DM voice connects and tears down cleanly.

### F. Optional — AI Study Assistant (#questions)

Requires `make product-demo-seed` (installs assistant + uploads an AI-approved resource).

1. **Learner** → open **#questions** (URL from seed output)
2. Post: *How do I submit homework before the deadline?*
3. Click **Ask AI**
4. Confirm a **grounded answer** with citations from “Homework Help Guide” in the right panel

Pass: HIGH-confidence answer with at least one source citation.

**Not generative LLM:** answers use local keyword matching over approved materials — see [`ai-study-assistant.md`](ai-study-assistant.md).

---

## 4. Tear down

```bash
make product-down
```

Logs and PIDs live under `.product/` (gitignored).

---

## Troubleshooting

| Symptom | Check |
|---------|--------|
| `make product-up` fails on realtime Docker build | Pull latest `main`; Dockerfile must copy the full `backend/` tree. |
| Gateway unhealthy | `tail -f .product/logs/gateway-service.log` |
| Live text not live | `curl -sf http://localhost:8087/actuator/health` — realtime must be UP |
| Voice join fails | `curl -sf http://localhost:7880` — LiveKit container running; check `.env` `LIVEKIT_*` keys |
| Friends list empty | Re-run seed script; confirm `make product-health` passes |
| Ask AI says not installed | Run `make product-demo-seed` |
| Ask AI low confidence | Question must share keywords with seeded `.txt` resource — see `ai-study-assistant.md` |
| Mic blocked | Browser site settings → allow microphone for `localhost` |

---

## Related

- One-command stack: [#62](https://github.com/Vinosaamaa/chanter/issues/62) — `make product-up`
- Issue breakdown: [`docs/issues/workable-product-issue-breakdown.md`](../issues/workable-product-issue-breakdown.md)
- How AI works: [`ai-study-assistant.md`](ai-study-assistant.md)
- Agent workflow: [`agent-workflow.md`](agent-workflow.md)
