export type StudyServerMember = {
  userId: string
  displayName: string
  email: string | null
  role: string
  staff: boolean
}

export type StudyServerMemberListResponse = {
  members: StudyServerMember[]
  filteredTotal: number
  memberCount: number
}

export type StudyServerMemberSummary = {
  memberCount: number
  preview: Array<{ userId: string; displayName: string }>
}

export type StudyServerMemberFilter = 'ALL' | 'STAFF' | 'LEARNERS'

export type CreatedStudyServerInvitation = {
  id: string
  email: string
}
