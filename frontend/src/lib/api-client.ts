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
let refreshInFlight: Promise<boolean> | null = null

export function configureApiAuth(config: ApiAuthConfig): void {
  apiAuthConfig = config
}

export async function apiFetch<T>(path: string, init?: ApiFetchInit): Promise<T> {
  const response = await fetchWithAuth(path, init, init?.skipAuthRefresh ?? false)
  return parseJsonResponse<T>(response)
}

export async function apiFetchBlob(path: string, init?: ApiFetchInit): Promise<Blob> {
  const response = await fetchWithAuth(path, init, init?.skipAuthRefresh ?? false)
  return response.blob()
}

async function fetchWithAuth(
  path: string,
  init: ApiFetchInit | undefined,
  skipAuthRefresh: boolean,
): Promise<Response> {
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
    refreshInFlight ??= apiAuthConfig.refreshSession().finally(() => {
      refreshInFlight = null
    })
    const refreshed = await refreshInFlight
    if (refreshed) {
      return fetchWithAuth(path, init, true)
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

  return response
}

async function parseJsonResponse<T>(response: Response): Promise<T> {
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
