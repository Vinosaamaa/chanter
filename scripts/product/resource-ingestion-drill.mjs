import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { setTimeout as delay } from 'node:timers/promises'

const base = `http://127.0.0.1:${process.env.GATEWAY_PORT ?? 8080}`
const agent = process.env.AGENT_SERVICE_URL ?? 'http://127.0.0.1:8085'
const media = process.env.MEDIA_SERVICE_URL ?? 'http://127.0.0.1:8084'
const internal = { 'X-Chanter-Internal-Service-Token': process.env.CHANTER_INTERNAL_SERVICE_TOKEN }
const origin = process.env.FRONTEND_ORIGIN ?? 'http://localhost:5173'
async function request(path, token, method = 'GET', body) {
  const response = await fetch(`${base}${path}`, {
    method, headers: { Origin: origin, 'X-Chanter-CSRF': '1', ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body instanceof FormData ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body), signal: AbortSignal.timeout(15_000),
  })
  assert(response.ok, `${method} resource API returned ${response.status}`)
  return response.status === 204 ? null : response.json()
}
async function chunks(id) {
  const response = await fetch(`${agent}/api/v1/internal/resource-chunks/${id}`, { headers: internal, signal: AbortSignal.timeout(10_000) })
  assert.equal(response.status, 200)
  return (await response.json()).chunks
}
async function poll(check) {
  for (let attempt = 0; attempt < 120; attempt++) {
    try { if (await check()) return } catch { /* restart may still be in progress */ }
    await delay(1000)
  }
  throw new Error('Resource ingestion convergence timed out')
}
let stopped = false
function stop() {
  execFileSync('bash', ['scripts/product/event-consumer-process.sh', 'stop', 'agent-service'], { stdio: 'pipe' })
  stopped = true
}
function start() {
  execFileSync('bash', ['scripts/product/event-consumer-process.sh', 'start', 'agent-service'], { stdio: 'pipe' })
  stopped = false
}
try {
  const owner = (await request('/api/v1/auth/login', null, 'POST', {
    email: process.env.DEMO_OWNER_EMAIL ?? 'dev-demo-owner@chanter.local', password: process.env.DEMO_PASSWORD ?? 'chanter-dev-demo',
  })).accessToken
  const server = (await request('/api/v1/study-servers', owner)).find(item => item.name === 'Workable Product Demo')
  assert(server)
  const navigation = await request(`/api/v1/study-servers/${server.id}/navigation`, owner)
  const course = navigation.courses.find(item => item.title === 'Workable Product Demo Course') ?? navigation.courses[0]
  assert(course)
  const resources = `/api/v1/courses/${course.id}/course-resources`
  const before = await request(`${resources}/usage`, owner)
  stop()
  const form = new FormData()
  form.append('file', new Blob(['Durable course evidence after worker restart.'], { type: 'text/plain' }), 'restart-evidence.txt')
  form.append('title', 'Resource ingestion restart proof')
  form.append('aiApproved', 'true')
  const uploaded = await request(resources, owner, 'POST', form)
  const path = `/api/v1/course-resources/${uploaded.id}`
  await poll(async () => {
    const current = await request(path, owner)
    return current.status === 'AVAILABLE' && ['PENDING', 'PROCESSING'].includes(current.ingestionStatus)
  })
  start()
  await poll(async () => (await request(path, owner)).ingestionStatus === 'READY')
  const indexed = await chunks(uploaded.id)
  assert.equal(indexed.length, 1)
  assert.equal(indexed[0].sourceScope.studyServerId, server.id)
  assert.equal(indexed[0].sourceScope.cohortId, null)
  assert.equal(indexed[0].sourceScope.language, 'und')
  assert.equal(indexed[0].sourceScope.accessScope, 'COURSE')
  assert.equal(indexed[0].sourceSha256, uploaded.sha256)
  stop()
  const revoked = await request(`${path}/ai-approval`, owner, 'PATCH', { aiApproved: false })
  assert.equal(revoked.aiApproved, false)
  assert.equal(revoked.ingestionStatus, 'NONE')
  const denied = await fetch(`${media}/api/v1/internal/resource-ingestion/${uploaded.id}/content?courseId=${course.id}&sha256=${uploaded.sha256}`,
    { headers: internal, signal: AbortSignal.timeout(10_000) })
  assert.equal(denied.status, 404)
  start()
  await poll(async () => (await chunks(uploaded.id)).length === 0)
  await request(`${path}/ai-approval`, owner, 'PATCH', { aiApproved: true })
  await poll(async () => (await request(path, owner)).ingestionStatus === 'READY')
  stop()
  await request(path, owner, 'DELETE')
  assert.equal((await request(`${resources}/usage`, owner)).reservedBytes, before.reservedBytes + uploaded.byteSize)
  start()
  await poll(async () => (await chunks(uploaded.id)).length === 0
    && (await request(`${resources}/usage`, owner)).reservedBytes === before.reservedBytes)
  console.log('Resource ingestion drill passed: stopped worker delivery, source scope, approval revocation, reapproval and deletion quota convergence.')
} finally {
  if (stopped) start()
}
