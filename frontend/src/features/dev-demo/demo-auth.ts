import { getApiBase } from '../../lib/api-base'
import type { AuthSession } from '../auth/types'

export type DemoPersonaKey = 'owner' | 'instructor' | 'learner' | 'nonEnrolled'

export type DemoPersona = {
  key: DemoPersonaKey
  userId: string
  accessToken: string
  refreshToken: string
  email: string
  displayName: string
}

export type DemoPersonas = Record<DemoPersonaKey, DemoPersona>

const DEMO_PASSWORD = 'chanter-dev-demo'

const PERSONA_LABELS: Record<DemoPersonaKey, string> = {
  owner: 'Demo Owner',
  instructor: 'Demo Instructor',
  learner: 'Demo Learner',
  nonEnrolled: 'Demo Stranger',
}

const TRANSIENT_AUTH_STATUSES = new Set([502, 503, 504])

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

async function login(email: string): Promise<Response> {
  const apiBase = getApiBase()
  return fetch(`${apiBase}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: DEMO_PASSWORD }),
  })
}

async function register(email: string, displayName: string): Promise<Response> {
  const apiBase = getApiBase()
  return fetch(`${apiBase}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: DEMO_PASSWORD, displayName }),
  })
}

async function loginWithRetry(email: string, attempts = 4): Promise<Response> {
  let lastResponse: Response | null = null

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const response = await login(email)
    if (response.ok || !TRANSIENT_AUTH_STATUSES.has(response.status)) {
      return response
    }

    lastResponse = response
    await delay(250 * (attempt + 1))
  }

  return lastResponse ?? login(email)
}

async function loginOrRegister(email: string, displayName: string): Promise<AuthSession> {
  const loginResponse = await loginWithRetry(email)
  if (loginResponse.ok) {
    return loginResponse.json() as Promise<AuthSession>
  }

  const registerResponse = await register(email, displayName)
  if (registerResponse.ok) {
    return registerResponse.json() as Promise<AuthSession>
  }

  if (registerResponse.status === 409) {
    const retryLoginResponse = await loginWithRetry(email)
    if (retryLoginResponse.ok) {
      return retryLoginResponse.json() as Promise<AuthSession>
    }

    const body = await retryLoginResponse.text().catch(() => '')
    throw new Error(
      `Demo auth bootstrap failed for ${email}: account exists but login failed (${retryLoginResponse.status}) ${body}`,
    )
  }

  const body = await registerResponse.text().catch(() => '')
  throw new Error(`Demo auth bootstrap failed for ${email}: ${registerResponse.status} ${body}`)
}

export async function bootstrapDemoPersonas(): Promise<DemoPersonas> {
  const personas: Partial<DemoPersonas> = {}

  for (const key of Object.keys(PERSONA_LABELS) as DemoPersonaKey[]) {
    const email = `dev-demo-${key}@chanter.local`
    const session = await loginOrRegister(email, PERSONA_LABELS[key])
    personas[key] = {
      key,
      userId: session.user.id,
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      email,
      displayName: session.user.displayName,
    }
  }

  return personas as DemoPersonas
}
