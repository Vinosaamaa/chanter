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

export async function register(input: RegisterInput): Promise<AuthSession> {
  return apiFetch<AuthSession>('/api/v1/auth/register', {
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
