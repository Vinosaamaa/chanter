export type CourseResource = {
  id: string
  courseId: string
  title: string
  fileName: string
  contentType: string
  byteSize: number
  aiApproved: boolean
  uploadedByUserId: string
  createdAt: string
}

export type CourseResourceListResponse = {
  courseResources: CourseResource[]
}

export type CourseResourceAccess = {
  courseId: string
  canUploadCourseResource: boolean
  canViewCourseResources: boolean
}

export type CourseResourceFilter = 'all' | 'pdf' | 'slides' | 'assignments' | 'other'
