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
  status?: 'PROCESSING' | 'AVAILABLE' | 'REJECTED' | 'FAILED'
  ingestionStatus?: 'NONE' | 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED' | 'EMPTY' | 'OCR_REQUIRED' | 'ENCRYPTED' | 'MALFORMED' | 'UNSUPPORTED' | 'LIMIT_EXCEEDED'
  ingestionSignals?: string[]
}

export type CourseResourceListResponse = {
  courseResources: CourseResource[]
}

export type CourseResourceAccess = {
  courseId: string
  canUploadCourseResource: boolean
  canViewCourseResources: boolean
}

export type CourseResourceFilter =
  | 'all'
  | 'pdf'
  | 'slides'
  | 'recordings'
  | 'assignments'
  | 'other'
