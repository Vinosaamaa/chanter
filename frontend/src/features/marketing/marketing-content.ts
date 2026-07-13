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
      '24/7 AI support that explains concepts, summarizes content, and helps students learn faster.',
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
      'Transparent help queue system so every student gets the support they need, when they need it.',
  },
  {
    id: 'instructor-dashboard',
    title: 'Instructor Dashboard',
    description:
      'Real-time insights into student activity, help requests, and course engagement.',
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
  headline: 'Free for educators to start',
  body: 'Launch a Study Server, enroll your first cohort, and explore AI-assisted support before upgrading your SaaS plan.',
}
