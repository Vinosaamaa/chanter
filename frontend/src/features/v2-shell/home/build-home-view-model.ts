import type { CourseAccentGradient } from '../course-accent'
import { courseAccentGradient } from '../course-accent'
import type { V2SidebarCourse } from '../hooks/use-v2-sidebar-data'

export type HomeAttentionItem = {
  id: string
  kind: 'office' | 'answer' | 'announcements'
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
  progress: number
}

export type HomeUpNextItem = {
  id: string
  title: string
  suffix?: string
  detail: string
  actionLabel?: string
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

function defaultProgress(title: string): number {
  const values = [65, 42, 28, 55]
  let hash = 0
  for (const char of title) {
    hash = (hash + char.charCodeAt(0)) % values.length
  }
  return values[hash] ?? 42
}

function splitCourseTitle(fullTitle: string): { code: string; title: string } {
  const parts = fullTitle.split('—').map((part) => part.trim())
  if (parts.length >= 2) {
    return { code: parts[0], title: parts.slice(1).join(' — ') }
  }
  return { code: fullTitle, title: fullTitle }
}

function accentForCourse(course: V2SidebarCourse, index: number): CourseAccentGradient {
  if (course.accentColor.includes('#')) {
    return { color: course.accentColor, colorEnd: course.accentColor }
  }
  return courseAccentGradient(course.id, index)
}

export function mapSidebarCourseToHomeCard(
  course: V2SidebarCourse,
  index: number,
): HomeCourseCard {
  const progress = defaultProgress(course.title)
  const { code, title } = splitCourseTitle(course.title)
  const accent = accentForCourse(course, index)

  return {
    id: course.id,
    serverId: course.serverId,
    code,
    title,
    professor: 'Instructor',
    cohortLabel: course.cohortLabel,
    color: accent.color,
    colorEnd: accent.colorEnd,
    progress,
  }
}

export function buildHomeViewModel(
  displayName: string,
  courses: V2SidebarCourse[],
  now = new Date(),
): HomeViewModel {
  const courseCards = courses.slice(0, 4).map((course, index) => mapSidebarCourseToHomeCard(course, index))
  const first = courseCards[0]
  const second = courseCards[1]
  const third = courseCards[2]

  const attention: HomeAttentionItem[] = []
  if (first) {
    attention.push({
      id: 'office-hours',
      kind: 'office',
      tone: 'purple',
      icon: 'calendar',
      headline: 'Office hours',
      suffix: ` · ${first.code} · 2:00 PM`,
      actionLabel: 'Join',
      actionVariant: 'button',
      href: `/app/servers/${first.serverId}/courses/${first.id}/office-hours`,
    })
  }
  if (second) {
    attention.push({
      id: 'question-answered',
      kind: 'answer',
      tone: 'green',
      icon: 'chat',
      headline: 'Your question was answered',
      suffix: ` · ${second.code}`,
      suffixOnNewLine: true,
      actionLabel: 'View answer',
      actionVariant: 'link',
      href: `/app/servers/${second.serverId}/courses/${second.id}/questions`,
    })
  }
  if (courseCards.length >= 2) {
    attention.push({
      id: 'announcements',
      kind: 'announcements',
      tone: 'blue',
      icon: 'megaphone',
      headline: 'Announcements',
      suffix: ` · ${first?.code ?? 'Course'}, ${third?.code ?? second?.code ?? 'Course'}`,
      suffixOnNewLine: true,
    })
  }

  const upNext: HomeUpNextItem[] = [
    {
      id: 'up-1',
      title: '2:00 PM',
      detail: `Office hours — ${first?.code ?? 'CS 101'}`,
      tone: 'purple',
      icon: 'calendar',
    },
    {
      id: 'up-2',
      title: 'Study room live',
      suffix: ' · 3 people',
      detail: second?.code ?? 'MATH 201',
      actionLabel: 'Join',
      tone: 'blue',
      icon: 'users',
    },
    {
      id: 'up-3',
      title: 'Problem Set 3',
      suffix: ' due Sunday',
      detail: first?.code ?? 'CS 101',
      tone: 'purple',
      icon: 'clipboard',
    },
    {
      id: 'up-4',
      title: 'Quiz 2',
      suffix: ` — ${third?.code ?? 'BIO 150'}`,
      detail: 'Tuesday',
      tone: 'purple',
      icon: 'calendar',
    },
    {
      id: 'up-5',
      title: 'Guest talk: AI in industry',
      detail: 'Jul 22',
      tone: 'purple',
      icon: 'calendar',
    },
  ]

  return {
    greeting: greetingForHour(now.getHours(), displayName),
    dateLabel: formatDateLabel(now),
    attention: attention.slice(0, 3),
    courses: courseCards,
    upNext,
  }
}
