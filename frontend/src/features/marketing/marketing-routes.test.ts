import { describe, expect, it } from 'vitest'

import {
  createStudyServerCta,
  isMarketingDemoEnabled,
  MARKETING_CREATE_SERVER_PATH,
  MARKETING_DEMO_PATH,
  MARKETING_SIGN_IN_PATH,
} from './marketing-routes'

describe('marketing-routes', () => {
  it('exposes stable marketing paths', () => {
    expect(MARKETING_SIGN_IN_PATH).toBe('/sign-in')
    expect(MARKETING_CREATE_SERVER_PATH).toBe('/app/onboarding/create-study-server')
    expect(MARKETING_DEMO_PATH).toBe('/dev/demo')
  })

  it('enables the marketing demo link only under Vite DEV (SEC-10)', () => {
    // Vitest runs with Vite's DEV mode; production builds replace import.meta.env.DEV with false.
    expect(isMarketingDemoEnabled()).toBe(true)
  })

  it('routes authenticated users directly to create-study-server', () => {
    expect(createStudyServerCta(true)).toEqual({ to: MARKETING_CREATE_SERVER_PATH })
  })

  it('routes visitors through sign-in with onboarding redirect', () => {
    expect(createStudyServerCta(false)).toEqual({
      to: MARKETING_SIGN_IN_PATH,
      state: { from: MARKETING_CREATE_SERVER_PATH },
    })
  })
})
