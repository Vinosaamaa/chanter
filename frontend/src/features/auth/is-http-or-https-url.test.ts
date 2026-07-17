import { describe, expect, it } from 'vitest'

import { isHttpOrHttpsUrl } from './is-http-or-https-url'

describe('isHttpOrHttpsUrl', () => {
  it('accepts http and https URLs', () => {
    expect(isHttpOrHttpsUrl('https://accounts.google.com/o/oauth2/v2/auth?client_id=x')).toBe(true)
    expect(isHttpOrHttpsUrl('http://localhost:8081/api/v1/auth/oauth/google/start')).toBe(true)
  })

  it('rejects dangerous or non-http schemes', () => {
    expect(isHttpOrHttpsUrl('javascript:alert(1)')).toBe(false)
    expect(isHttpOrHttpsUrl('data:text/html,hi')).toBe(false)
    expect(isHttpOrHttpsUrl('vbscript:msgbox(1)')).toBe(false)
  })

  it('rejects blank and relative values', () => {
    expect(isHttpOrHttpsUrl('')).toBe(false)
    expect(isHttpOrHttpsUrl('   ')).toBe(false)
    expect(isHttpOrHttpsUrl(null)).toBe(false)
    expect(isHttpOrHttpsUrl('/oauth/start')).toBe(false)
  })
})
