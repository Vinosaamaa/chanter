import { isV2CommunityRoute, isV2CourseRoute, resolveV2PrimaryNav } from './v2-routes'

export type V2SearchConfig = {
  placeholder: string
  scopeLabel: string
}

export function resolveV2SearchConfig(pathname: string): V2SearchConfig {
  const primary = resolveV2PrimaryNav(pathname)

  if (primary === 'teaching') {
    return { placeholder: 'Search your teaching…', scopeLabel: 'Teaching' }
  }
  if (primary === 'inbox') {
    return { placeholder: 'Search inbox…', scopeLabel: 'Inbox' }
  }
  if (primary === 'calendar') {
    return { placeholder: 'Search calendar…', scopeLabel: 'Calendar' }
  }
  if (primary === 'friends') {
    return { placeholder: 'Search friends and messages…', scopeLabel: 'Friends' }
  }
  if (isV2CourseRoute(pathname)) {
    if (pathname.endsWith('/questions')) {
      return { placeholder: 'Search @questions', scopeLabel: 'Questions' }
    }
    if (pathname.endsWith('/chat')) {
      return { placeholder: 'Search in:#general', scopeLabel: 'Chat' }
    }
    if (pathname.endsWith('/resources')) {
      return { placeholder: 'Search in: resources', scopeLabel: 'Resources' }
    }
    return { placeholder: 'Search this course…', scopeLabel: 'Course' }
  }
  if (isV2CommunityRoute(pathname)) {
    return { placeholder: 'Search this hub…', scopeLabel: 'Hub' }
  }

  return { placeholder: 'Search your courses…', scopeLabel: 'Home' }
}
