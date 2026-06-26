import type { CourseResource, CourseResourceFilter } from './course-resource-types'

export function formatByteSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function resourceFileKind(resource: CourseResource): CourseResourceFilter {
  const lowerName = resource.fileName.toLowerCase()
  const lowerTitle = resource.title.toLowerCase()

  if (lowerName.endsWith('.pdf') || resource.contentType === 'application/pdf') {
    if (lowerName.includes('assignment') || lowerTitle.includes('assignment')) {
      return 'assignments'
    }
    return 'pdf'
  }

  if (
    lowerName.endsWith('.pptx') ||
    lowerName.endsWith('.ppt') ||
    resource.contentType.includes('presentation')
  ) {
    return 'slides'
  }

  return 'other'
}

export function resourceKindLabel(kind: CourseResourceFilter): string {
  switch (kind) {
    case 'pdf':
      return 'PDF'
    case 'slides':
      return 'Slides'
    case 'assignments':
      return 'Assignment'
    default:
      return 'File'
  }
}

export function matchesResourceFilter(
  resource: CourseResource,
  filter: CourseResourceFilter,
): boolean {
  if (filter === 'all') {
    return true
  }
  return resourceFileKind(resource) === filter
}

export function matchesResourceSearch(resource: CourseResource, query: string): boolean {
  const normalized = query.trim().toLowerCase()
  if (!normalized) {
    return true
  }
  return (
    resource.title.toLowerCase().includes(normalized) ||
    resource.fileName.toLowerCase().includes(normalized)
  )
}

export function isPdfResource(resource: CourseResource): boolean {
  return (
    resource.fileName.toLowerCase().endsWith('.pdf') || resource.contentType === 'application/pdf'
  )
}
