export type CohortRosterStatus = 'ENROLLED' | 'PENDING'
export type CohortRosterRole = 'INSTRUCTOR' | 'TA' | 'LEARNER'

export type CohortRosterMember = {
  userId: string
  invitationId: string | null
  displayName: string
  email: string | null
  role: CohortRosterRole
  status: CohortRosterStatus
  assignedTeachingAssistantUserId: string | null
  enrolledAt: string | null
}

export type CohortRoster = {
  cohortId: string
  instructor: CohortRosterMember
  teachingAssistants: CohortRosterMember[]
  learners: CohortRosterMember[]
  learnerCount: number
  teachingAssistantCount: number
  pendingCount: number
  limit: number
  offset: number
}

export type CohortInvitation = {
  id: string
  userId: string
  displayName: string
  email: string
  status: 'PENDING'
  createdAt: string
}
