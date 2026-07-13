export type V2PrimaryNav = 'home' | 'teaching' | 'inbox' | 'calendar' | 'friends'

export type V2CourseTab =
  | 'overview'
  | 'chat'
  | 'questions'
  | 'resources'
  | 'office-hours'
  | 'people'

export type V2CommunityTab =
  | 'announcements'
  | 'lounge'
  | 'events'
  | 'discover'
  | 'members'

export function v2HomePath(): string {
  return '/app/home'
}

export function v2TeachingPath(): string {
  return '/app/teaching'
}

export function v2InboxPath(): string {
  return '/app/inbox'
}

export function v2CalendarPath(): string {
  return '/app/calendar'
}

export function v2FriendsPath(): string {
  return '/app/friends'
}

export function v2CoursePath(
  serverId: string,
  courseId: string,
  tab: V2CourseTab = 'overview',
): string {
  return `/app/servers/${serverId}/courses/${courseId}/${tab}`
}

export function v2CommunityPath(
  serverId: string,
  tab: V2CommunityTab = 'announcements',
): string {
  return `/app/servers/${serverId}/community/${tab}`
}

export function v2JoinCreatePath(): string {
  return '/app/onboarding/join-or-create'
}

export function resolveV2PrimaryNav(pathname: string): V2PrimaryNav | null {
  if (pathname === '/app/home' || pathname === '/app' || pathname === '/app/') {
    return 'home'
  }
  if (pathname.startsWith('/app/teaching')) {
    return 'teaching'
  }
  if (pathname.startsWith('/app/inbox')) {
    return 'inbox'
  }
  if (pathname.startsWith('/app/calendar')) {
    return 'calendar'
  }
  if (pathname.startsWith('/app/friends')) {
    return 'friends'
  }
  return null
}

export function isV2CourseRoute(pathname: string): boolean {
  return /\/app\/servers\/[^/]+\/courses\/[^/]+\//.test(pathname)
}

export function isV2CommunityRoute(pathname: string): boolean {
  return /\/app\/servers\/[^/]+\/community\//.test(pathname)
}
