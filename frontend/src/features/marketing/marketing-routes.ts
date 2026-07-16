export const MARKETING_SIGN_IN_PATH = '/sign-in'
export const MARKETING_CREATE_SERVER_PATH = '/app/onboarding/create-study-server'
/** Local Vite DEV harness only — omitted from production builds (SEC-10). */
export const MARKETING_DEMO_PATH = '/dev/demo'
export const MARKETING_DOCS_URL = 'https://github.com/Vinosaamaa/chanter/blob/main/README.md'

export function isMarketingDemoEnabled(): boolean {
  return import.meta.env.DEV
}

export type MarketingCtaLink = {
  to: string
  state?: { from: string }
}

export function createStudyServerCta(isAuthenticated: boolean): MarketingCtaLink {
  if (isAuthenticated) {
    return { to: MARKETING_CREATE_SERVER_PATH }
  }

  return {
    to: MARKETING_SIGN_IN_PATH,
    state: { from: MARKETING_CREATE_SERVER_PATH },
  }
}
