#!/usr/bin/env node
// Only the three local demo personas may be verified through the local test inbox.
import { setTimeout as delay } from 'node:timers/promises'

const allowed = new Set([
  'dev-demo-owner@chanter.local',
  'dev-demo-member@chanter.local',
  'dev-demo-learner@chanter.local',
])
const email = process.argv[2]
const origin = new URL(process.env.CHANTER_PUBLIC_BASE_URL ?? 'http://localhost:5173')
const gateway = new URL(process.env.GATEWAY ?? `http://localhost:${process.env.GATEWAY_PORT ?? 8080}`)
const loopback = (url) => ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)

if (process.env.CHANTER_EMAIL_LOCAL_SINK !== 'true'
  || !allowed.has(email) || !loopback(origin) || !loopback(gateway)) {
  throw new Error('Demo verification requires a configured loopback email sink and a demo persona.')
}

async function json(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(5_000) })
  if (!response.ok) throw new Error('Local demo inbox is unavailable.')
  return response.json()
}

let verified = false
for (let attempt = 0; attempt < 30 && !verified; attempt += 1) {
  const inbox = await json('http://127.0.0.1:8025/api/v1/messages?limit=100')
  const candidates = inbox.messages.filter((message) =>
    message.Subject === 'Verify your Chanter email'
    && message.To.some((recipient) => recipient.Address === email))
  for (const candidate of candidates) {
    const message = await json(`http://127.0.0.1:8025/api/v1/message/${encodeURIComponent(candidate.ID)}`)
    const rawLink = message.Text.match(/https?:\/\/[^\s<>]+\/verify-email\?token=[a-f0-9]+/i)?.[0]
    if (!rawLink) continue
    const link = new URL(rawLink)
    if (link.origin !== origin.origin) throw new Error('Demo email link has an unexpected origin.')
    const result = await fetch(new URL('/api/v1/auth/verify-email', gateway), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Origin: origin.origin, 'X-Chanter-CSRF': '1' },
      body: JSON.stringify({ token: link.searchParams.get('token') }),
      signal: AbortSignal.timeout(5_000),
    })
    if (result.ok) { verified = true; break }
  }
  if (!verified) await delay(1_000)
}
if (!verified) throw new Error('Demo verification email was not delivered or could not be used.')
