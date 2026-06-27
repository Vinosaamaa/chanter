export type GlobalSearchDocumentType = 'RESOURCE' | 'FAQ'

export type GlobalSearchHit = {
  documentType: GlobalSearchDocumentType
  courseId: string
  courseTitle: string
  sourceId: string
  title: string
  snippet: string
}

export type GlobalSearchResponse = {
  results: GlobalSearchHit[]
}

export type ReindexResponse = {
  indexedDocuments: number
}
