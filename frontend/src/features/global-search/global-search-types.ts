export type GlobalSearchDocumentType = 'RESOURCE' | 'FAQ' | 'MESSAGE' | 'EVENT' | 'ANNOUNCEMENT'

export type GlobalSearchHit = {
  documentType: GlobalSearchDocumentType
  courseId: string | null
  courseTitle: string
  sourceId: string
  title: string
  snippet: string
  href?: string | null
}

export type GlobalSearchResponse = {
  results: GlobalSearchHit[]
}

export type ReindexResponse = {
  indexedDocuments: number
}
