# AI Study Assistant — how it works today

This doc explains what “AI” means in Chanter **right now**, what is **not** connected yet, and how to try it locally.

---

## Short answer

| Question | Answer |
|----------|--------|
| Do we call OpenAI, Anthropic, or another LLM API? | **No — not in the current codebase.** |
| Does the Study Assistant “work”? | **Yes** — but answers come from a **local keyword-matching engine** over **your approved course materials**, not from a generative language model. |
| What does “install” do? | It is **permissions + product plumbing**: which channels and resources the assistant may read. It is not downloading a model. |
| How do we get “real” generative AI later? | Planned as a separate **Agent Runtime** layer (`plan.md`) with provider adapters (e.g. Ollama locally, hosted APIs in production). That is **not wired in yet**. |

---

## What happens when a learner clicks “Ask AI”

1. Learner posts a **Support Question** in **#questions**.
2. Learner clicks **Ask AI** (or you invoke the REST endpoint).
3. `agent-service` checks:
   - Study Assistant is **installed** in this Study Server
   - **#questions** channel is in the assistant’s **grants**
   - Learner is enrolled and is the question author
   - SaaS **AI quota** is not exhausted
4. The service loads **grounding sources**:
   - **AI-approved** course resources (`.txt`, `.md` only) that were granted at install time
   - **Approved FAQs** from the course (if any)
5. `KeywordGroundingEngine` scores the question against those texts:
   - Tokenizes the question (drops short/stop words)
   - Finds the resource with the most matching terms
   - Returns an excerpt as the answer with **citations**
6. Outcome:
   - **HIGH confidence** → answer + sources; question marked `AI_ANSWERED`
   - **LOW confidence** → handoff message; question marked `AI_LOW_CONFIDENCE`; learner can **Add to TA Queue**

Implementation: `backend/agent-service/.../KeywordGroundingEngine.java`

There is **no HTTP call** to OpenAI, Anthropic, Gemini, or similar in `application.yml` or the agent service today.

---

## What “install” means

Install is a **human-in-the-loop grant confirmation** (built in Education MVP #18):

| Step | Who | What |
|------|-----|------|
| Preview | Instructor | `GET .../study-assistant/install-preview` — lists candidate channels, courses, cohorts, and AI-approved resources |
| Confirm | Instructor | `POST .../study-assistant/install` — saves explicit grants (one assistant per Study Server), or use the **Install AI Study Assistant** dialog in `#questions` |
| Presence | Anyone with access | `GET .../study-assistant?viewerUserId=` — shows installed + visible grants |

Grants types: `STUDY_SERVER_CHANNEL`, `COURSE`, `COHORT`, `COURSE_CHANNEL`, `COURSE_RESOURCE`.

Without install, **Ask AI** returns “not installed”. Without **AI-approved resources** (or FAQs) whose text matches the question, you get **low confidence** handoff.

**Install UI (production):** Study Server owners and instructors can install from the `#questions` context panel (**Install AI Study Assistant**). See `workable-product-demo.md` § F. The seed script (`make product-demo-seed`) still works for a one-shot demo without manual install.

---

## How to try it locally (after `make product-up`)

### Automated (recommended)

```bash
make product-demo-seed
```

This now also:

- Uploads an AI-approved **Homework Help Guide** (`.txt`)
- Installs the Study Assistant with grants for the demo course and resources **on a clean demo stack**

If the assistant was already installed from a prior run, re-seeding uploads the resource but **does not add new grants** — reset the local DB or use a fresh stack for a clean AI demo.

### Manual UI steps

1. Sign in as **learner**: `dev-demo-learner@chanter.local` / `chanter-dev-demo`
2. Open **#questions** (URL printed by seed script)
3. Post a support question that overlaps the seeded doc, for example:
   > How do I submit homework before the deadline?
4. Click **Ask AI**
5. Check the answer card and **Grounding sources** in the right panel

For **low-confidence** behavior, ask something unrelated, e.g. “What is the weather in Tokyo?”

---

## Tips for answers that “work”

The engine needs **at least two matching terms** (3+ letters, not stop words) between question and resource text.

| Works better | Works poorly |
|--------------|--------------|
| Questions using words from your uploaded `.txt` / `.md` | Vague or off-topic questions |
| AI-approved resources with clear prose | PDFs (content not parsed today) |
| Approved FAQs with Q+A text | Empty or missing grants |

---

## Roadmap: actual LLM integration

From `plan.md` and architecture docs:

- **Today:** `agent-service` = install, grants, quotas, grounded answers via `KeywordGroundingEngine`
- **#94:** AI-approved `.txt`/`.md` uploads are chunked with stable offsets into `resource_chunks`
- **#95:** Chunks are embedded (default hashing embedder; optional Ollama — see `local-embeddings.md`) and stored for grant-scoped top-k retrieval
- **#96:** Replace keyword grounding with RAG over those vectors
- **Later:** `agent-runtime-service` (planned) = LLM orchestration, provider adapters, streaming, tools
- **Local dev option (planned):** Ollama
- **Production option (planned):** hosted LLM APIs with budgets and audit

Swapping the engine means implementing `GroundingEngine` (or a successor interface) with an LLM-backed implementation while keeping install, grants, quotas, and citations.

---

## Related APIs

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/study-servers/{id}/study-assistant/install-preview` | HITL install preview |
| `POST /api/v1/study-servers/{id}/study-assistant/install` | Confirm install |
| `GET /api/v1/study-servers/{id}/study-assistant` | Installed + grants |
| `POST /api/v1/course-channels/{channelId}/support-questions/{id}/assistant-answer` | Generate answer |

Service: `backend/agent-service` (port **8085**, started by `make product-up`).

---

## Related docs

- [`getting-started.md`](getting-started.md) — run the stack + optional AI section
- [`workable-product-demo.md`](workable-product-demo.md) — full E2E checklist including AI
- [`backend/agent-service/README.md`](../../backend/agent-service/README.md) — API summary
