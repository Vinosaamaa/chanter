import type { ShellCourse, StudyServerNavigation } from './types'

export type CourseChannelContext = {
  course: ShellCourse
  channel: ShellCourse['channels'][number]
}

export function studyChannelPath(serverId: string, channelId: string): string {
  return `/app/servers/${serverId}/study-channels/${channelId}`
}

export function courseChannelPath(serverId: string, channelId: string): string {
  return `/app/servers/${serverId}/course-channels/${channelId}`
}

export function defaultChannelPath(
  serverId: string,
  navigation: StudyServerNavigation,
): string | null {
  const studyChannel = navigation.studyServerChannels.find((channel) => channel.kind === 'TEXT')
    ?? navigation.studyServerChannels[0]
  if (studyChannel) {
    return studyChannelPath(serverId, studyChannel.id)
  }

  for (const course of navigation.courses) {
    const courseChannel = course.channels.find((channel) => channel.kind === 'TEXT')
      ?? course.channels[0]
    if (courseChannel) {
      return courseChannelPath(serverId, courseChannel.id)
    }
  }

  return null
}

export function findChannelLabel(
  navigation: StudyServerNavigation | undefined,
  scope: 'study' | 'course',
  channelId: string,
): string | null {
  if (!navigation) {
    return null
  }

  if (scope === 'study') {
    return navigation.studyServerChannels.find((channel) => channel.id === channelId)?.name ?? null
  }

  for (const course of navigation.courses) {
    const channel = course.channels.find((item) => item.id === channelId)
    if (channel) {
      return `${course.title} / ${channel.name}`
    }
  }

  return null
}

export function findCourseChannelContext(
  navigation: StudyServerNavigation | undefined,
  channelId: string,
): CourseChannelContext | null {
  if (!navigation) {
    return null
  }

  for (const course of navigation.courses) {
    const channel = course.channels.find((item) => item.id === channelId)
    if (channel) {
      return { course, channel }
    }
  }

  return null
}

export function isQuestionsChannel(context: CourseChannelContext | null): boolean {
  return context?.channel.kind === 'TEXT' && context.channel.name === 'questions'
}

export function isResourcesChannel(context: CourseChannelContext | null): boolean {
  return context?.channel.kind === 'TEXT' && context.channel.name === 'resources'
}
