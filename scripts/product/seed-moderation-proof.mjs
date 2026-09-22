// Hosted fixture bootstrap uses the real non-web operator command and public APIs.
import { readdirSync, writeFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

if (process.env.GITHUB_ACTIONS !== 'true' || process.env.RUNNER_OS !== 'Linux'
  || process.env.CHANTER_EMAIL_LOCAL_SINK !== 'true') throw new Error('Hosted isolated product fixture required')
const origin = 'http://127.0.0.1:9419'
const login = await fetch(`${origin}/api/v1/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', Origin: origin, 'X-Chanter-CSRF': '1' },
  body: JSON.stringify({ email: 'dev-demo-owner@chanter.local', password: process.env.DEMO_PASSWORD ?? 'chanter-dev-demo' }),
  signal: AbortSignal.timeout(10000),
})
if (!login.ok) throw new Error(`Fixture operator login failed: ${login.status}`)
const session = await login.json()
const user = JSON.parse(Buffer.from(session.accessToken.split('.')[1], 'base64url')).sub
if (!/^[a-f0-9-]{36}$/.test(user)) throw new Error('Fixture login returned no account reference')
const jar = readdirSync('backend/auth-service/target').find(name => /^auth-service-.*\.jar$/.test(name))
if (!jar) throw new Error('Built auth service required')
const bootstrap = spawnSync('java', ['-jar', `backend/auth-service/target/${jar}`,
  '--spring.main.web-application-type=none', `--chanter.moderation.bootstrap-user=${user}`,
  '--chanter.moderation.bootstrap-reason=Issue 249 isolated hosted acceptance fixture'],
{ timeout: 60000, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 })
// The command output contains no credentials; retain locally with other service logs.
writeFileSync('.product/logs/moderation-bootstrap.log', bootstrap.stdout + bootstrap.stderr)
if (bootstrap.status !== 0) throw new Error('Non-web operator bootstrap failed; inspect the scoped bootstrap log')
console.log('Isolated operator bootstrap completed through the production command. No production operator was configured.')
