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
  refreshSession: () => Promise<boolean>
}

export type ApiFetchInit = RequestInit & {
  skipAuthRefresh?: boolean
}

let apiAuthConfig: ApiAuthConfig | null = null

export function configureApiAuth(config: ApiAuthConfig): void {
  apiAuthConfig = config
}

export async function apiFetch<T>(path: string, init?: ApiFetchInit): Promise<T> {
  return apiFetchWithRetry<T>(path, init, init?.skipAuthRefresh ?? false)
}

async function apiFetchWithRetry<T>(
  path: string,
  init: ApiFetchInit | undefined,
  skipAuthRefresh: boolean,
): Promise<T> {
  const headers = new Headers(init?.headers)
  if (init?.body && typeof init.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const accessToken = apiAuthConfig?.getAccessToken()
  if (accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${getApiBase()}${path}`, {
    ...init,
    headers,
  })

  if (response.status === 401 && !skipAuthRefresh && apiAuthConfig) {
    const refreshed = await apiAuthConfig.refreshSession()
    if (refreshed) {
      return apiFetchWithRetry<T>(path, init, true)
    }
  }

  if (!response.ok) {
    const body = await response.text().catch(() => undefined)
    throw new ApiError(
      `Request failed: ${response.status} ${response.statusText}`,
      response.status,
      body,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
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
