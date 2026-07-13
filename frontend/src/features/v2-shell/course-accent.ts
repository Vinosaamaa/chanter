/** Course accent gradients — from Chativity MockUp UI reference. */
export type CourseAccentGradient = {
  color: string
  colorEnd: string
}

export const COURSE_ACCENT_GRADIENTS: CourseAccentGradient[] = [
  { color: '#4564ee', colorEnd: '#4768ff' },
  { color: '#55a63a', colorEnd: '#6fbe47' },
  { color: '#ffbf09', colorEnd: '#ffc500' },
  { color: '#8e50d2', colorEnd: '#a45ade' },
  { color: '#4965ee', colorEnd: '#5a70f7' },
  { color: '#5da83c', colorEnd: '#6aaa43' },
  { color: '#ffc000', colorEnd: '#ffd033' },
  { color: '#7555d8', colorEnd: '#8a65e8' },
]

export function courseAccentGradient(seed: string, index = 0): CourseAccentGradient {
  let hash = index
  for (const char of seed) {
    hash = (hash + char.charCodeAt(0)) % COURSE_ACCENT_GRADIENTS.length
  }
  return COURSE_ACCENT_GRADIENTS[hash] ?? COURSE_ACCENT_GRADIENTS[0]
}

/** @deprecated use courseAccentGradient().color */
export function courseAccentColor(seed: string): string {
  return courseAccentGradient(seed).color
}
