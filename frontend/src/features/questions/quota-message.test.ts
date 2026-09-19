import { describe, expect, it } from 'vitest'
import { quotaExhaustedMessage } from './questions-api'

describe('free-beta quota recovery', () => {
  it('offers course resources and human support without promising an upgrade', () => {
    expect(quotaExhaustedMessage(undefined)).toBe('AI Study Assistant quota exhausted. Course resources and instructor support remain available.')
  })
})
