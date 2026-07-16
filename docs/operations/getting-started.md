# Getting started (complete beginner guide)

**You do not need to read the whole repo.** This page tells you how to run Chanter on your laptop and try each major feature step by step.

Chanter is a learning-community app (think Discord for courses): Study Servers, text channels, voice rooms, friends, and direct messages.

---

## What you need first

Install these once on your computer:

| Tool | Why | Check it works |
|------|-----|----------------|
| **Git** | Download the code | `git --version` |
| **Docker Desktop** | Database, cache, realtime, voice server | Docker app is running; `docker ps` |
| **Java 21+** | Backend services | `java -version` |
| **Maven 3.9+** | Build backend | `mvn -version` |
| **Node 20+** | Frontend website | `node -version` |

On Mac, Java 21 is often easiest via [Homebrew](https://brew.sh): `brew install openjdk@21`.

---

## Part 1 — Start the website (first time)

Do this from a terminal in the folder where you want the project.

### Step 1 — Get the code

```bash
git clone https://github.com/Vinosaamaa/chanter.git
cd chanter
```

### Step 2 — Create your local config

```bash
make product-env
```

This copies `.env.example` → `.env` (if needed) and fills **unique** `CHANTER_JWT_SECRET` / `CHANTER_INTERNAL_SERVICE_TOKEN` values. Do not paste the old in-repo example secrets — they are rejected on purpose (SEC-04).

`make product-env` also adds `DEMO_PASSWORD=chanter-dev-demo` when missing (required for `make product-demo-seed`). You can change that password; both demo accounts use the same local value.

### Step 3 — Start everything

```bash
make product-up
```

**What this does (in plain English):**

1. Starts Docker containers: database, Redis, message bus, file storage, **realtime chat**, and **LiveKit** (voice).
2. Builds and starts all backend microservices (auth, courses, messages, search, etc.).
3. Starts the React website at **http://localhost:5173**.

The first run can take several minutes (downloads + Maven build). Wait until the command finishes without errors.

### Step 4 — Check that it is healthy

```bash
make product-health
```

You want green / successful checks for gateway, auth, realtime, and LiveKit.

### Step 5 — Open the site

In your browser go to:

**http://localhost:5173**

You should see the Chanter landing page. Click **Sign in** or go to **http://localhost:5173/sign-in**.

---

## Part 2 — Prepare demo accounts (recommended)

The fastest way to try everything is the seed script. It creates two test users, a Study Server, a course, enrollment, a friendship, an **AI-approved course resource**, and installs the **AI Study Assistant**.

```bash
make product-demo-seed
```

Default logins (password for both: **`chanter-dev-demo`**):

| Role | Email | Good for trying |
|------|-------|-----------------|
| Owner / instructor | `dev-demo-owner@chanter.local` | Creating content, instructor tools |
| Learner | `dev-demo-learner@chanter.local` | Learner view, DM, voice |

The script prints **direct links** to channels — copy those into your browser.

**Tip:** Use two browser windows — one normal, one private/incognito — so you can be signed in as both users at once.

---

## Part 3 — What you can do in the app

Below is **where to click** for each feature. Paths assume you are signed in and have run the seed script (or created your own Study Server).

### Sign in / create account

1. Go to **http://localhost:5173/sign-in**
2. Enter email + password → **Sign in**
3. Or switch to **Create account**, add display name, register

After sign-in you land in the app at `/app/...`.

### Browse a Study Server (main layout)

The app has a Discord-like layout:

- **Left column** — list of Study Servers you belong to
- **Second column** — channels for the selected server (study channels + courses)
- **Center** — the open channel (chat, voice, resources, etc.)
- **Top bar** — Friends, Instructor Dashboard, Search, Sign out

Open the seeded server from the left column, or use the link from `make product-demo-seed`.

### Live text chat in a channel

**Study channels** (server-wide): `#announcements`, `#general`  
**Course channels** (under a course): `#announcements`, `#questions`, `#resources`

1. Click a **text** channel in the sidebar (e.g. **#announcements** under the demo course)
2. Type in the message box at the bottom → press Enter or Send
3. Open the **same channel** in a second browser as the other user — messages appear **without refreshing** (live WebSocket)

### #questions — support questions + AI Study Assistant

1. Open **#questions** under a course (after `make product-demo-seed`, use the URL the script prints)
2. Post a support question — for the seeded demo, try:
   > How do I submit homework before the deadline?
3. Click **Ask AI**
4. Read the answer and **Grounding sources** in the right panel

**Important:** This is **not** ChatGPT or OpenAI today. The assistant matches your question against **AI-approved course files** using a local keyword engine. See [`ai-study-assistant.md`](ai-study-assistant.md) for the full picture.

To get **low confidence** (TA handoff), ask something unrelated to the uploaded materials, e.g. “What is the weather in Tokyo?”

### #resources — Course Resources

1. Open **#resources**
2. Search or filter files
3. Instructors can upload resources (upload controls on that page)

### Global search

1. Click **Search** in the top bar, or press **⌘K** (Mac) / **Ctrl+K** (Windows/Linux)
2. Type a query to search FAQs and resources in the current Study Server

### Friends and direct messages (DM)

1. Top bar → **Friends** (or **http://localhost:5173/app/friends**)
2. Click a friend in the left list
3. Type a message → Send
4. The other user opens **Friends**, selects you — messages appear live

**Note:** Friend requests are available in the production Friends UI. The seed script still creates a demo friendship for convenience. The legacy API playground at `/dev/demo` is **Vite DEV only** (not in production builds).

### Voice channel (hear and speak)

1. In the sidebar, open **study-room** (a voice channel under Study Server channels)
2. Click **Join voice**
3. Allow microphone when the browser asks
4. A second user joins the same channel — both appear under **Connected members**
5. Use **Mute** / **Leave voice** when done

Use headphones to avoid echo.

### DM voice call (1:1 with a friend)

1. **Friends** → select a friend
2. Click **Call**
3. Other user sees incoming call → **Accept**
4. Speak; **Hang up** when finished

### Instructor Dashboard

1. Top bar → **Instructor Dashboard**
2. Pick a Study Server
3. View metrics, SaaS plan usage, and instructor operations entry points

### Support operations (instructor)

From a course in the sidebar, instructors can open:

- **TA queue**
- **Office hours**
- **FAQ approval**

URLs look like: `/app/servers/{serverId}/courses/{courseId}/support/ta-queue` (and similar for other ops).

### Create your own Study Server (no seed)

1. Sign in
2. Go to **http://localhost:5173/app/onboarding/create-study-server**
3. Enter a name → create
4. Add a course from server home, enroll learners from the enrollment page

---

## Part 4 — Full two-user demo (proof everything works)

For a checklist that walks Owner + Learner through chat, DM, voice, and optional DM call:

**[`workable-product-demo.md`](workable-product-demo.md)**

That doc is the official “workable local product” definition.

---

## Part 5 — Day-to-day commands

| When | Command |
|------|---------|
| Start the stack | `make product-up` |
| Check health | `make product-health` |
| Load demo users + server | `make product-demo-seed` |
| Stop everything | `make product-down` |
| View service logs | `ls .product/logs/` then `tail -f .product/logs/gateway-service.log` (etc.) |

**Only infra (no app):** `make infra-up` — Postgres/Redis/etc. without starting Java or the website. Most people should use `make product-up` instead.

---

## Part 6 — Something broke?

| Problem | What to try |
|---------|-------------|
| `make product-up` fails | Is Docker Desktop running? Pull latest code. |
| Page won’t load | Run `make product-health`. Is port 5173 free? |
| Sign-in fails | Gateway down? Check `curl http://localhost:8080/actuator/health` |
| Messages not live | Realtime down? Check `curl http://localhost:8087/actuator/health` |
| Voice won’t connect | Allow mic in browser; check LiveKit / Docker `livekit` container |
| Friends list empty | Run `make product-demo-seed` again |
| Port already in use | `make product-down`, wait a few seconds, `make product-up` |

---

## Part 7 — Other URLs (optional)

| URL | What it is |
|-----|------------|
| http://localhost:5173 | Main website (use this) |
| http://localhost:8080/actuator/health | Backend gateway health |
| http://localhost:5173/dev/demo | Legacy developer API playground (**`npm run dev` only** — omitted from production builds) |

---

## Where to go next

| Audience | Document |
|----------|----------|
| Run + demo (you) | This page + [`workable-product-demo.md`](workable-product-demo.md) |
| How AI works today (no OpenAI yet) | [`ai-study-assistant.md`](ai-study-assistant.md) |
| Product vision & mockups | [`docs/product-design/README.md`](../product-design/README.md) |
| Domain words (Study Server, Cohort, …) | [`CONTEXT.md`](../../CONTEXT.md) |
| Agents / contributors | [`HANDOFF.md`](../../HANDOFF.md), [`agent-workflow.md`](agent-workflow.md) |
