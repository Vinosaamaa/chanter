import { describe, expect, it } from 'vitest'

import { ApiError } from './api-client'
import { formatUserFacingApiError, isUnauthorizedApiError } from './format-api-error'

describe('format-api-error', () => {
  it('detects unauthorized ApiError', () => {
    expect(isUnauthorizedApiError(new ApiError('Request failed: 401 Unauthorized', 401))).toBe(true)
    expect(isUnauthorizedApiError(new ApiError('Request failed: 403 Forbidden', 403))).toBe(false)
  })

  it('maps 401 to a session-expired message', () => {
    expect(
      formatUserFacingApiError(
        new ApiError('Request failed: 401 Unauthorized', 401),
        'fallback',
      ),
    ).toBe('Your session expired. Please sign in again.')
  })
})
