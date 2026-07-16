import { apiFetch } from '../../lib/api-client'
import type { AuthSession, AuthUser } from './types'

export type RegisterInput = {
  email: string
  password: string
  displayName: string
}

export type LoginInput = {
  email: string
  password: string
}

export type RegisterResponse =
  | AuthSession
  | { verificationRequired: true; message: string }

export async function register(input: RegisterInput): Promise<RegisterResponse> {
  return apiFetch<RegisterResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
    skipAuthRefresh: true,
  })
}

export async function login(input: LoginInput): Promise<AuthSession> {
  return apiFetch<AuthSession>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(input),
    skipAuthRefresh: true,
  })
}

export async function refreshSession(refreshToken: string): Promise<AuthSession> {
  return apiFetch<AuthSession>('/api/v1/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
    skipAuthRefresh: true,
  })
}

export async function logout(refreshToken: string): Promise<void> {
  await apiFetch<void>('/api/v1/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
    skipAuthRefresh: true,
  })
}

export async function fetchCurrentUser(accessToken: string): Promise<AuthUser> {
  return apiFetch<AuthUser>('/api/v1/auth/me', {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    skipAuthRefresh: true,
  })
}

export async function forgotPassword(email: string): Promise<{ message: string }> {
  return apiFetch<{ message: string }>('/api/v1/auth/forgot-password', {
    method: 'POST',
    body: JSON.stringify({ email }),
    skipAuthRefresh: true,
  })
}

export async function resetPassword(token: string, password: string): Promise<{ message: string }> {
  return apiFetch<{ message: string }>('/api/v1/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify({ token, password }),
    skipAuthRefresh: true,
  })
}

export async function verifyEmail(token: string): Promise<{ message: string }> {
  return apiFetch<{ message: string }>('/api/v1/auth/verify-email', {
    method: 'POST',
    body: JSON.stringify({ token }),
    skipAuthRefresh: true,
  })
}

export type OAuthProvider = {
  id: string
  label: string
  authorizationUrl: string
}

export async function fetchOauthProviders(): Promise<{ providers: OAuthProvider[] }> {
  return apiFetch<{ providers: OAuthProvider[] }>('/api/v1/auth/oauth/providers', {
    skipAuthRefresh: true,
  })
}

export async function completeGoogleOauth(code: string, state: string): Promise<AuthSession> {
  return apiFetch<AuthSession>('/api/v1/auth/oauth/google/callback', {
    method: 'POST',
    body: JSON.stringify({ code, state }),
    skipAuthRefresh: true,
  })
}

export function isAuthSession(value: RegisterResponse): value is AuthSession {
  return 'accessToken' in value
}
