import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { setTimeout as delay } from 'node:timers/promises'

const base = `http://127.0.0.1:${process.env.GATEWAY_PORT ?? 8080}`
const community = process.env.COMMUNITY_SERVICE_URL ?? 'http://127.0.0.1:8082'
const tokenHeader = { 'X-Chanter-Internal-Service-Token': process.env.CHANTER_INTERNAL_SERVICE_TOKEN }
const origin = process.env.FRONTEND_ORIGIN ?? 'http://localhost:5173'
async function request(path, token, method = 'GET', body) {
  const response = await fetch(`${base}${path}`, {
    method, headers: { 'Content-Type': 'application/json', Origin: origin, 'X-Chanter-CSRF': '1',
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10_000),
  })
  assert(response.ok, `${method} product API returned ${response.status}`)
  return response.status === 204 ? null : response.json()
}
async function poll(check) {
  for (let attempt = 0; attempt < 90; attempt++) {
    try { if (await check()) return } catch { /* service may still be starting */ }
    await delay(1000)
  }
  throw new Error('Durable event convergence timed out')
}
const stopped = new Set()
function stop(module) {
  execFileSync('bash', ['scripts/product/event-consumer-process.sh', 'stop', module], { stdio: 'pipe' })
  stopped.add(module)
}
function start(module) {
  execFileSync('bash', ['scripts/product/event-consumer-process.sh', 'start', module], { stdio: 'pipe' })
  stopped.delete(module)
}
try {
  const login = async email => (await request('/api/v1/auth/login', null, 'POST', {
    email, password: process.env.DEMO_PASSWORD ?? 'chanter-dev-demo',
  })).accessToken
  const owner = await login(process.env.DEMO_OWNER_EMAIL ?? 'dev-demo-owner@chanter.local')
  const member = await login(process.env.DEMO_MEMBER_EMAIL ?? 'dev-demo-member@chanter.local')
  const server = (await request('/api/v1/study-servers', owner)).find(server => server.name === 'Workable Product Demo')
  assert(server)
  const path = `/api/v1/study-servers/${server.id}/announcements`
  const title = `Restart proof ${Date.now()}`
  for (const module of ['notification-service', 'search-service']) stop(module)
  const announcement = await request(path, owner, 'POST', { title: `${title} initial`, body: 'Durable announcement before restart.' })
  await request(`${path}/${announcement.id}`, owner, 'PATCH', { title, body: 'Updated while both consumers were stopped.' })
  const removed = await request(path, owner, 'POST', { title: `${title} archived`, body: 'Must not remain in search or Inbox.' })
  await request(`${path}/${removed.id}/archive`, owner, 'POST')
  const statsResponse = await fetch(`${community}/api/v1/internal/events/outbox`, { headers: tokenHeader })
  assert.equal(statsResponse.status, 200)
  const stats = await statsResponse.json()
  assert(stats.pending + stats.sending > 0)
  for (const module of ['notification-service', 'search-service']) start(module)
  const searchPath = `/api/v1/study-servers/${server.id}/search?q=${encodeURIComponent(title)}`
  let visibleNotification
  await poll(async () => {
    const results = (await request(searchPath, member)).results
    const notifications = (await request('/api/v1/me/notifications', member)).notifications
    const matches = notifications.filter(item => item.sourceId === announcement.id)
    visibleNotification = matches[0]
    return results.some(item => item.sourceId === announcement.id && item.title === title)
      && !results.some(item => item.sourceId === removed.id) && matches.length === 1
      && visibleNotification.title === title && visibleNotification.unread
      && !notifications.some(item => item.sourceId === removed.id)
  })
  assert((await request('/api/v1/me/notifications/unread-count', member)).unreadCount >= 1)
  await request(`/api/v1/me/notifications/${visibleNotification.id}/read`, member, 'POST')
  for (const module of ['notification-service', 'search-service']) { stop(module); start(module) }
  await poll(async () => {
    const notifications = (await request('/api/v1/me/notifications', member)).notifications.filter(item => item.sourceId === announcement.id)
    return notifications.length === 1 && !notifications[0].unread
      && (await request(searchPath, member)).results.some(item => item.sourceId === announcement.id)
  })
  writeFileSync('.product/durable-events-proof.json', JSON.stringify({ serverId: server.id, sourceId: announcement.id, title }))
  console.log('Durable event drill passed: stopped consumers, updates and archive, persisted restart, unique Inbox delivery and read state.')
} finally {
  for (const module of stopped) start(module)
}
