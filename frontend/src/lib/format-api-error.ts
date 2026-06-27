import { ApiError } from './api-client'

export function isUnauthorizedApiError(caught: unknown): boolean {
  return caught instanceof ApiError && caught.status === 401
}

export function formatUserFacingApiError(caught: unknown, fallback: string): string {
  if (caught instanceof ApiError) {
    if (caught.status === 401) {
      return 'Your session expired. Please sign in again.'
    }
    if (caught.status === 403) {
      return 'You do not have permission for this action.'
    }
    return `Request failed (${caught.status}).`
  }
  if (caught instanceof Error && caught.message.trim().length > 0) {
    return caught.message
  }
  return fallback
}
