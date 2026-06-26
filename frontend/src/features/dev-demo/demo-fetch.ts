import type { DemoPersona, DemoPersonaKey, DemoPersonas } from './demo-auth'

let demoPersonas: DemoPersonas | null = null
let activePersonaKey: DemoPersonaKey = 'owner'
let demoFetchInstalled = false
const originalFetch: typeof fetch = globalThis.fetch.bind(globalThis)

export function setDemoPersonas(personas: DemoPersonas): void {
  demoPersonas = personas
}

export function setActiveDemoPersona(key: DemoPersonaKey): void {
  activePersonaKey = key
}

export function getDemoPersonas(): DemoPersonas {
  if (!demoPersonas) {
    throw new Error('Demo personas are not initialized')
  }
  return demoPersonas
}

export function getDemoPersona(key: DemoPersonaKey): DemoPersona {
  return getDemoPersonas()[key]
}

function resolvePersonaKeyFromUrl(url: string): DemoPersonaKey | null {
  const match = url.match(
    /(?:viewerUserId|userId|instructorUserId|actorUserId|learnerUserId|uploaderUserId|approvedByUserId)=([^&]+)/,
  )
  if (!match) {
    return null
  }

  const userId = decodeURIComponent(match[1])
  const personas = getDemoPersonas()
  for (const key of Object.keys(personas) as DemoPersonaKey[]) {
    if (personas[key].userId === userId) {
      return key
    }
  }

  return null
}

function isPublicAuthPath(url: string): boolean {
  return (
    url.includes('/api/v1/auth/health') ||
    url.includes('/api/v1/auth/register') ||
    url.includes('/api/v1/auth/login') ||
    url.includes('/api/v1/auth/refresh') ||
    url.includes('/api/v1/auth/logout')
  )
}

export async function demoFetch(
  persona: DemoPersonaKey | DemoPersona,
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  const resolved = typeof persona === 'string' ? getDemoPersona(persona) : persona
  const headers = new Headers(init?.headers)
  if (!headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${resolved.accessToken}`)
  }
  if (init?.body && typeof init.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  return originalFetch(input, {
    ...init,
    headers,
  })
}

export function installAuthenticatedDemoFetch(): void {
  if (demoFetchInstalled) {
    return
  }

  globalThis.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
    const url =
      typeof input === 'string'
        ? input
        : input instanceof URL
          ? input.toString()
          : input.url

    if (!url.includes('/api/v1/') || isPublicAuthPath(url)) {
      return originalFetch(input, init)
    }

    const personaKey = resolvePersonaKeyFromUrl(url) ?? activePersonaKey
    return demoFetch(personaKey, input, init)
  }) as typeof fetch

  demoFetchInstalled = true
}
