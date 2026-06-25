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

async function loginOrRegister(email: string, displayName: string): Promise<AuthSession> {
  const apiBase = getApiBase()
  const loginResponse = await fetch(`${apiBase}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: DEMO_PASSWORD }),
  })

  if (loginResponse.ok) {
    return loginResponse.json() as Promise<AuthSession>
  }

  const registerResponse = await fetch(`${apiBase}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: DEMO_PASSWORD, displayName }),
  })

  if (!registerResponse.ok) {
    const body = await registerResponse.text().catch(() => '')
    throw new Error(`Demo auth bootstrap failed for ${email}: ${registerResponse.status} ${body}`)
  }

  return registerResponse.json() as Promise<AuthSession>
}

export async function bootstrapDemoPersonas(): Promise<DemoPersonas> {
  const entries = await Promise.all(
    (Object.keys(PERSONA_LABELS) as DemoPersonaKey[]).map(async (key) => {
      const email = `dev-demo-${key}@chanter.local`
      const session = await loginOrRegister(email, PERSONA_LABELS[key])
      const persona: DemoPersona = {
        key,
        userId: session.user.id,
        accessToken: session.accessToken,
        refreshToken: session.refreshToken,
        email,
        displayName: session.user.displayName,
      }
      return [key, persona] as const
    }),
  )

  return Object.fromEntries(entries) as DemoPersonas
}
