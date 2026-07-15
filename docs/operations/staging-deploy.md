# Staging deployment with HTTPS (#101)

This runbook deploys Chanter on a **single VM** (or similar PaaS host) behind **Caddy** for automatic TLS. It is the supported path for public/staging demos before a multi-node production topology.

## Public staging URL

| Environment | URL | Notes |
|-------------|-----|--------|
| **Placeholder (until DNS exists)** | `https://staging.chanter.example` | Replace with the real hostname after DNS + host provisioning |
| Local product stack | `http://localhost:5173` | `make product-up` — not HTTPS |

Until a real hostname is provisioned, keep `CHANTER_PUBLIC_BASE_URL` unset locally and use the placeholder only in staging secrets / Caddy config. **Do not commit real secrets.**

## Architecture (single VM)

```
Internet
   │
   ▼
Caddy (:443 TLS)
   ├─ /                → frontend static (Vite build) or Vite preview
   ├─ /api/**          → gateway-service :8080  (HTTP + WebSocket upgrade)
   └─ /livekit/**      → LiveKit :7880         (optional path proxy)
Backend services on host loopback (8081–8089, 8087 realtime)
Docker Compose: Postgres, Redis, Redpanda, MinIO, LiveKit
```

WebSocket path used by the app: `/api/v1/realtime/ws` (gateway → realtime-service).  
LiveKit browser URL should be `wss://<host>/livekit` (or a dedicated LiveKit subdomain) when TLS terminates at Caddy.

## Prerequisites

- Ubuntu 22.04+ (or similar) with Docker + Docker Compose
- Java 21, Maven, Node 20+
- DNS A/AAAA record for the staging hostname pointing at the VM
- Open ports: **80**, **443** (and LiveKit UDP media ports if not path-proxied — typically **7881–7882/UDP**)

## Secrets (never commit)

Copy `.env.example` → `/etc/chanter/.env` (or `$HOME/chanter/.env`) and set:

| Variable | Purpose |
|----------|---------|
| `CHANTER_PUBLIC_BASE_URL` | `https://staging.chanter.example` (no trailing slash) |
| `CHANTER_JWT_SECRET` | New random ≥32 chars (not the local example) |
| `CHANTER_INTERNAL_SERVICE_TOKEN` | New random ≥32 chars |
| `POSTGRES_PASSWORD` / `MINIO_ROOT_PASSWORD` | Strong staging values |
| `DEMO_PASSWORD` | Optional; omit on shared staging if demo seed is disabled |
| `LIVEKIT_URL` | Public `wss://…` URL browsers use |
| `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | Staging LiveKit credentials |
| `VITE_API_BASE` | Usually empty when Caddy serves `/api` same-origin |

Email / SSO (#102) vars are documented in `.env.example` (§ Production auth).

### Auth on staging (#102)

| Setting | Staging recommendation |
|---------|------------------------|
| `CHANTER_AUTH_REQUIRE_EMAIL_VERIFICATION` | `true` |
| `CHANTER_EMAIL_PROVIDER` | `log` (links appear in auth-service logs) or wire an SMTP relay later |
| `CHANTER_OAUTH_GOOGLE_CLIENT_ID` / `SECRET` | optional; enables Continue with Google |
| OAuth redirect URI | `${CHANTER_PUBLIC_BASE_URL}/oauth/callback/google` |

Walkthrough: register → copy verify link from auth logs → `/verify-email?token=` → sign in. Forgot password uses the same email log path.

## Deploy steps

1. **Clone and env**

```bash
git clone https://github.com/Vinosaamaa/chanter.git /opt/chanter
cd /opt/chanter
sudo mkdir -p /etc/chanter
sudo cp .env.example /etc/chanter/.env
# edit /etc/chanter/.env — set secrets + CHANTER_PUBLIC_BASE_URL
ln -sf /etc/chanter/.env .env
```

2. **Infra**

```bash
docker compose -f infra/docker-compose.yml up -d
# wait until healthy
```

3. **Backend**

```bash
export JAVA_HOME=…   # Java 21
make product-up      # or product-supervise on fragile hosts
make product-health
# optional: make product-demo-seed
```

4. **Frontend static build**

```bash
cd frontend
npm ci
npm run build
# serve with Caddy `file_server` from frontend/dist
```

5. **Caddy TLS**

Install Caddy, then use [`infra/staging/Caddyfile.example`](../../infra/staging/Caddyfile.example):

```bash
sudo cp infra/staging/Caddyfile.example /etc/caddy/Caddyfile
# replace staging.chanter.example with your hostname
sudo systemctl reload caddy
```

Caddy obtains certificates via ACME (Let’s Encrypt) when DNS points at the VM.

## Smoke checklist

After deploy:

1. `https://<host>/` loads the marketing landing page over HTTPS.
2. `https://<host>/sign-in` loads (certificate valid).
3. Sign in (demo or real user) → app shell loads.
4. Open a course chat channel — realtime messages work (WebSocket through TLS proxy).
5. Start or join Office Hours / voice — LiveKit connects via `wss://` (check browser console for signaling errors).
6. `curl -fsS https://<host>/api/actuator/health` (or gateway health path) returns healthy.

## WebSocket and LiveKit notes

- **Realtime:** Browser connects to `wss://<host>/api/v1/realtime/ws`. Caddy must allow WebSocket upgrade on `/api/*` (see example Caddyfile).
- **LiveKit:** Prefer proxying `/livekit/*` to `localhost:7880` and set `LIVEKIT_URL=wss://<host>/livekit`. Ensure UDP media ports are open on the host firewall, or use LiveKit Cloud for staging media.
- Vite’s local proxy is **not** used in staging; same-origin `/api` via Caddy replaces it.

## Rollback / teardown

```bash
make product-down
docker compose -f infra/docker-compose.yml down
# Caddy can stay up serving a maintenance page if desired
```

## Related

- Local demo: [`getting-started.md`](getting-started.md), [`workable-product-demo.md`](workable-product-demo.md)
- Change log: [`issue-101-change-log.md`](issue-101-change-log.md)
- Auth productionization: issue **#102**
