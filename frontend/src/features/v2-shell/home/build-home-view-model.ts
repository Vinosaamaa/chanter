import type { CourseAccentGradient } from '../course-accent'
import { courseAccentGradient } from '../course-accent'
import type {
  HomeSummaryAttentionItem,
  HomeSummaryCourse,
  HomeSummaryResponse,
  HomeSummaryUpNextItem,
} from '../../home/home-summary-types'

export type HomeAttentionItem = {
  id: string
  kind: 'office' | 'answer' | 'announcements' | 'event'
  tone: 'purple' | 'green' | 'blue'
  icon: 'calendar' | 'chat' | 'megaphone'
  headline: string
  suffix?: string
  suffixOnNewLine?: boolean
  actionLabel?: string
  actionVariant?: 'button' | 'link'
  href?: string
}

export type HomeCourseCard = {
  id: string
  serverId: string
  code: string
  title: string
  professor: string
  cohortLabel: string
  color: string
  colorEnd: string
  progress?: number | null
  href: string
}

export type HomeUpNextItem = {
  id: string
  title: string
  suffix?: string
  detail: string
  actionLabel?: string
  href?: string
  tone: 'purple' | 'blue'
  icon: 'calendar' | 'users' | 'clipboard'
}

export type HomeViewModel = {
  greeting: string
  dateLabel: string
  attention: HomeAttentionItem[]
  courses: HomeCourseCard[]
  upNext: HomeUpNextItem[]
}

function greetingForHour(hour: number, displayName: string): string {
  if (hour < 12) {
    return `Good morning, ${displayName}`
  }
  if (hour < 17) {
    return `Good afternoon, ${displayName}`
  }
  return `Good evening, ${displayName}`
}

function formatDateLabel(date: Date): string {
  return date.toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  })
}

function splitCourseTitle(fullTitle: string): { code: string; title: string } {
  const parts = fullTitle.split('—').map((part) => part.trim())
  if (parts.length >= 2) {
    return { code: parts[0], title: parts.slice(1).join(' — ') }
  }
  return { code: fullTitle, title: fullTitle }
}

function accentForCourse(courseId: string, index: number): CourseAccentGradient {
  return courseAccentGradient(courseId, index)
}

function mapAttentionKind(kind: string): HomeAttentionItem['kind'] {
  switch (kind) {
    case 'OFFICE_HOURS':
      return 'office'
    case 'ANNOUNCEMENTS':
      return 'announcements'
    case 'EVENT':
      return 'event'
    default:
      return 'announcements'
  }
}

function mapAttentionVisual(kind: string): Pick<HomeAttentionItem, 'tone' | 'icon'> {
  switch (kind) {
    case 'OFFICE_HOURS':
      return { tone: 'purple', icon: 'calendar' }
    case 'ANNOUNCEMENTS':
      return { tone: 'blue', icon: 'megaphone' }
    case 'EVENT':
      return { tone: 'purple', icon: 'calendar' }
    default:
      return { tone: 'blue', icon: 'megaphone' }
  }
}

function mapUpNextVisual(kind: string): Pick<HomeUpNextItem, 'tone' | 'icon'> {
  switch (kind) {
    case 'STUDY_ROOM':
      return { tone: 'blue', icon: 'users' }
    case 'OFFICE_HOURS':
    case 'EVENT':
    default:
      return { tone: 'purple', icon: 'calendar' }
  }
}

export function mapHomeSummaryCourse(
  course: HomeSummaryCourse,
  index: number,
): HomeCourseCard {
  const { code, title } = splitCourseTitle(course.title)
  const accent = accentForCourse(course.courseId, index)
  return {
    id: course.courseId,
    serverId: course.studyServerId,
    code,
    title,
    professor: course.instructorDisplayName?.trim() || 'Instructor',
    cohortLabel: course.cohortName?.trim() || 'Cohort',
    color: accent.color,
    colorEnd: accent.colorEnd,
    progress: course.progress ?? null,
    href: course.href,
  }
}

export function mapHomeSummaryAttention(item: HomeSummaryAttentionItem): HomeAttentionItem {
  const visual = mapAttentionVisual(item.kind)
  return {
    id: item.id,
    kind: mapAttentionKind(item.kind),
    tone: visual.tone,
    icon: visual.icon,
    headline: item.headline,
    suffix: item.suffix ?? undefined,
    suffixOnNewLine: item.suffixOnNewLine,
    actionLabel: item.actionLabel ?? undefined,
    actionVariant: item.actionVariant === 'link' ? 'link' : 'button',
    href: item.href ?? undefined,
  }
}

export function mapHomeSummaryUpNext(item: HomeSummaryUpNextItem): HomeUpNextItem {
  const visual = mapUpNextVisual(item.kind)
  return {
    id: item.id,
    title: item.title,
    suffix: item.suffix ?? undefined,
    detail: item.detail,
    actionLabel: item.actionLabel ?? undefined,
    href: item.href ?? undefined,
    tone: visual.tone,
    icon: visual.icon,
  }
}

export function buildHomeViewModel(
  displayName: string,
  summary: HomeSummaryResponse | null | undefined,
  now = new Date(),
): HomeViewModel {
  const courses = (summary?.courses ?? []).slice(0, 4).map((course, index) =>
    mapHomeSummaryCourse(course, index),
  )
  const attention = (summary?.attention ?? []).map(mapHomeSummaryAttention)
  const upNext = (summary?.upNext ?? []).map(mapHomeSummaryUpNext)

  return {
    greeting: greetingForHour(now.getHours(), displayName),
    dateLabel: formatDateLabel(now),
    attention,
    courses,
    upNext,
  }
}
