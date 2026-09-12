export type MarketingFeatureId =
  | 'ai-assistant'
  | 'course-channels'
  | 'ta-queue'
  | 'instructor-dashboard'

export type MarketingFeature = {
  id: MarketingFeatureId
  title: string
  description: string
}

export const MARKETING_FEATURES: MarketingFeature[] = [
  {
    id: 'ai-assistant',
    title: 'AI Study Assistant',
    description:
      'Ask questions grounded in approved Course materials, with source references and a route to human help.',
  },
  {
    id: 'course-channels',
    title: 'Course Channels',
    description:
      'Organized spaces for lectures, discussions, and collaboration, designed around the course.',
  },
  {
    id: 'ta-queue',
    title: 'TA Queue',
    description:
      'Give teaching assistants a shared queue of questions that need a closer look.',
  },
  {
    id: 'instructor-dashboard',
    title: 'Instructor Dashboard',
    description:
      'See open questions, upcoming Office Hours and support requests across your Courses.',
  },
]

export type MarketingPricingTeaser = {
  headline: string
  body: string
}

export const MARKETING_USE_CASES: string[] = [
  'University courses and bootcamps',
  'Cohort-based online programs',
  'Tutoring businesses and study groups',
]

export const MARKETING_PRICING_TEASER: MarketingPricingTeaser = {
  headline: 'Start with your learning community.',
  body: 'Create a Study Server for your group, add a Course, and invite your first learners.',
}
