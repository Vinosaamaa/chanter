import { describe, expect, it } from 'vitest'

import { resolveV2PrimaryNav, v2CoursePath, v2HomePath, v2JoinCreatePath } from './v2-routes'
import { resolveV2SearchConfig } from './v2-search-config'

describe('v2-routes', () => {
  it('builds canonical home path', () => {
    expect(v2HomePath()).toBe('/app/home')
  })

  it('builds course workspace paths', () => {
    expect(v2CoursePath('srv-1', 'course-1', 'chat')).toBe(
      '/app/servers/srv-1/courses/course-1/chat',
    )
  })

  it('builds the join-or-create chooser path', () => {
    expect(v2JoinCreatePath()).toBe('/app/onboarding/join-or-create')
  })

  it('resolves primary nav from pathname', () => {
    expect(resolveV2PrimaryNav('/app/home')).toBe('home')
    expect(resolveV2PrimaryNav('/app/inbox')).toBe('inbox')
    expect(resolveV2PrimaryNav('/app/friends/requests')).toBe('friends')
    expect(resolveV2PrimaryNav('/app/servers/x/courses/y/overview')).toBeNull()
  })
})

describe('v2-search-config', () => {
  it('returns route-scoped placeholders', () => {
    expect(resolveV2SearchConfig('/app/home').placeholder).toBe('Search your courses…')
    expect(resolveV2SearchConfig('/app/inbox').placeholder).toBe('Search inbox…')
    expect(resolveV2SearchConfig('/app/servers/a/courses/b/chat').placeholder).toBe(
      'Search in:#general',
    )
    expect(resolveV2SearchConfig('/app/servers/a/courses/b/questions').placeholder).toBe(
      'Search @questions',
    )
  })
})
