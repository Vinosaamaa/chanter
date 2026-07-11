import type { ShellChannel } from './types'
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

export type CourseChannelGroup = 'information' | 'text' | 'voice'

export type ShellContextPanelKind =
  | 'questions'
  | 'resources'
  | 'general'
  | 'voice'
  | 'placeholder'

export function studyChannelGroup(channel: ShellChannel): CourseChannelGroup {
  if (channel.kind === 'VOICE') {
    return 'voice'
  }

  if (channel.name === 'announcements') {
    return 'information'
  }

  return 'text'
}

export function courseChannelGroup(channel: ShellCourse['channels'][number]): CourseChannelGroup {
  return studyChannelGroup(channel)
}

export function resolveCourseCohortId(course: ShellCourse): string | undefined {
  return course.cohorts.length === 1 ? course.cohorts[0]?.id : undefined
}

export function courseChannelGroupLabel(group: CourseChannelGroup): string {
  switch (group) {
    case 'information':
      return 'Information'
    case 'text':
      return 'Text channels'
    case 'voice':
      return 'Voice channels'
  }
}

export function resolveShellContextPanelKind(
  pathname: string,
  channelId: string | undefined,
  navigation: StudyServerNavigation | undefined,
): ShellContextPanelKind {
  if (!channelId || !navigation) {
    return 'placeholder'
  }

  if (pathname.includes('/course-channels/')) {
    const context = findCourseChannelContext(navigation, channelId)
    if (!context) {
      return 'placeholder'
    }
    if (isQuestionsChannel(context)) {
      return 'questions'
    }
    if (isResourcesChannel(context)) {
      return 'resources'
    }
    if (context.channel.kind === 'VOICE') {
      return 'voice'
    }
    return 'general'
  }

  if (pathname.includes('/study-channels/')) {
    const studyChannel = findStudyChannel(navigation, channelId)
    if (!studyChannel) {
      return 'placeholder'
    }
    return studyChannel.kind === 'VOICE' ? 'voice' : 'general'
  }

  return 'placeholder'
}

export function channelDescription(
  scope: 'study' | 'course',
  channelName: string,
): string | null {
  const courseDescriptions: Record<string, string> = {
    general: 'Course-wide chat. Be respectful and help each other learn!',
    questions: 'Post support questions and ask the AI Study Assistant for grounded help.',
    resources: 'Upload and browse AI-approved course materials.',
    announcements: 'Important updates from instructors and teaching staff.',
    'study-room': 'Voice study room for live collaboration.',
  }

  const studyDescriptions: Record<string, string> = {
    general: 'Study Server-wide chat. Be respectful and help each other learn!',
    announcements: 'Important updates for the whole Study Server.',
    'study-room': 'Voice study room for live collaboration.',
  }

  if (scope === 'study') {
    return studyDescriptions[channelName] ?? 'Study Server channel.'
  }

  return courseDescriptions[channelName] ?? 'Course channel.'
}

export function channelBreadcrumb(
  navigation: StudyServerNavigation | undefined,
  scope: 'study' | 'course',
  channelId: string,
): { courseTitle: string | null; channelName: string } | null {
  if (!navigation) {
    return null
  }

  if (scope === 'study') {
    const channel = findStudyChannel(navigation, channelId)
    return channel ? { courseTitle: null, channelName: channel.name } : null
  }

  const context = findCourseChannelContext(navigation, channelId)
  return context
    ? { courseTitle: context.course.title, channelName: context.channel.name }
    : null
}

export function channelIcon(channel: ShellChannel): string {
  if (channel.kind === 'VOICE') {
    return '🔊'
  }
  if (channel.name === 'announcements') {
    return '📣'
  }
  return '#'
}
