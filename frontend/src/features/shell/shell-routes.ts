import type { SupportOperation } from '../support-operations/support-operations-types'
import type { ShellCourse, StudyServerNavigation } from './types'

export type { SupportOperation }

export const SUPPORT_OPERATIONS: SupportOperation[] = [
  'ta-queue',
  'office-hours',
  'faq-approval',
]

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

export function channelSummaryPath(serverId: string, channelId: string): string {
  return `/app/servers/${serverId}/course-channels/${channelId}/summary`
}

export function supportOperationPath(
  serverId: string,
  courseId: string,
  operation: SupportOperation,
): string {
  return `/app/servers/${serverId}/courses/${courseId}/support/${operation}`
}

export function supportOperationLabel(operation: SupportOperation): string {
  switch (operation) {
    case 'ta-queue':
      return 'TA queue'
    case 'office-hours':
      return 'Office Hours'
    case 'faq-approval':
      return 'FAQ approval'
  }
}

export function isSupportOperation(value: string | undefined): value is SupportOperation {
  return value === 'ta-queue' || value === 'office-hours' || value === 'faq-approval'
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
    return findStudyChannel(navigation, channelId)?.name ?? null
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

export function findCourseById(
  navigation: StudyServerNavigation | undefined,
  courseId: string | undefined,
): ShellCourse | null {
  if (!navigation || !courseId) {
    return null
  }

  return navigation.courses.find((course) => course.id === courseId) ?? null
}

export function findQuestionsChannel(course: ShellCourse) {
  return course.channels.find((channel) => channel.kind === 'TEXT' && channel.name === 'questions')
}

export function isQuestionsChannel(context: CourseChannelContext | null): boolean {
  return context?.channel.kind === 'TEXT' && context.channel.name === 'questions'
}

export function isResourcesChannel(context: CourseChannelContext | null): boolean {
  return context?.channel.kind === 'TEXT' && context.channel.name === 'resources'
}

export function findStudyChannel(
  navigation: StudyServerNavigation | undefined,
  channelId: string,
) {
  return navigation?.studyServerChannels.find((channel) => channel.id === channelId) ?? null
}

export function isVoiceStudyChannel(
  navigation: StudyServerNavigation | undefined,
  channelId: string,
): boolean {
  return findStudyChannel(navigation, channelId)?.kind === 'VOICE'
}
