import { getApiBase } from './api-base'

export class ApiError extends Error {
  status: number
  body?: string

  constructor(message: string, status: number, body?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

type ApiAuthConfig = {
  getAccessToken: () => string | null
  getSessionGeneration: () => string | number
  refreshSession: () => Promise<boolean>
}

export type ApiFetchInit = RequestInit & {
  skipAuthRefresh?: boolean
}

let apiAuthConfig: ApiAuthConfig | null = null
let refreshInFlight: Promise<boolean> | null = null

export function configureApiAuth(config: ApiAuthConfig): void {
  apiAuthConfig = config
}

/** Current bearer access token from the configured auth store. */
export function getApiAccessToken(): string | null {
  return apiAuthConfig?.getAccessToken() ?? null
}

/** Single-flight refresh used by HTTP and WebSocket reconnect paths. */
export async function refreshApiSession(): Promise<boolean> {
  if (!apiAuthConfig) {
    return false
  }

  refreshInFlight ??= apiAuthConfig.refreshSession().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

export async function apiFetch<T>(path: string, init?: ApiFetchInit): Promise<T> {
  const checkSession = sessionGuard(init)
  const response = await fetchWithAuth(path, init, init?.skipAuthRefresh ?? false, checkSession)
  const result = await parseJsonResponse<T>(response, checkSession)
  checkSession()
  return result
}

export async function apiFetchBlob(path: string, init?: ApiFetchInit): Promise<Blob> {
  const checkSession = sessionGuard(init)
  const response = await fetchWithAuth(path, init, init?.skipAuthRefresh ?? false, checkSession)
  const result = await response.blob()
  checkSession()
  return result
}

/** Auth-aware fetch that returns the raw Response (for SSE / streaming bodies). */
export async function apiFetchResponse(path: string, init?: ApiFetchInit): Promise<Response> {
  return fetchWithAuth(path, init, init?.skipAuthRefresh ?? false, sessionGuard(init))
}

function sessionGuard(init?: ApiFetchInit): () => void {
  const generation = apiAuthConfig?.getSessionGeneration()
  return () => {
    if (init?.signal?.aborted || (!init?.skipAuthRefresh && generation !== apiAuthConfig?.getSessionGeneration())) {
      throw new DOMException('The account changed while this request was running.', 'AbortError')
    }
  }
}

async function fetchWithAuth(
  path: string,
  init: ApiFetchInit | undefined,
  skipAuthRefresh: boolean,
  checkSession: () => void,
): Promise<Response> {
  checkSession()
  const headers = new Headers(init?.headers)
  if (init?.body && typeof init.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (path.startsWith('/api/v1/auth/') && !['GET', 'HEAD'].includes(init?.method?.toUpperCase() ?? 'GET')) {
    headers.set('X-Chanter-CSRF', '1')
  }

  const accessToken = apiAuthConfig?.getAccessToken()
  if (accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${getApiBase()}${path}`, {
    ...init,
    credentials: 'include',
    headers,
  })
  checkSession()

  if (response.status === 401 && !skipAuthRefresh && apiAuthConfig) {
    const refreshed = accessToken !== apiAuthConfig.getAccessToken() || await refreshApiSession()
    checkSession()
    if (refreshed) {
      return fetchWithAuth(path, init, true, checkSession)
    }
  }

  if (!response.ok) {
    const body = await response.text().catch(() => undefined)
    checkSession()
    throw new ApiError(
      `Request failed: ${response.status} ${response.statusText}`,
      response.status,
      body,
    )
  }

  return response
}

async function parseJsonResponse<T>(response: Response, checkSession: () => void): Promise<T> {
  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  checkSession()
  if (!text) {
    return undefined as T
  }

  try {
    return JSON.parse(text) as T
  } catch {
    throw new ApiError('Response body was not valid JSON', response.status, text)
  }
}

export type HealthResponse = {
  status: string
  service?: string
}

export async function fetchGatewayHealth(): Promise<HealthResponse> {
  return apiFetch<HealthResponse>('/actuator/health')
}
