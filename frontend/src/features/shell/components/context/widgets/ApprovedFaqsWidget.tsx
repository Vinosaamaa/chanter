import { useEffect, useState } from 'react'

import { Link } from 'react-router-dom'

import { useAuthStore } from '../../../../../stores/auth-store'
import { listApprovedFaqs } from '../../../../support-operations/faq-api'
import type { ApprovedFaq } from '../../../../support-operations/support-operations-types'
import { supportOperationPath } from '../../../shell-routes'

import { ContextWidgetSection } from '../ContextPanelFrame'

export function ApprovedFaqsWidget({
  serverId,
  courseId,
  limit = 4,
}: {
  serverId: string
  courseId: string
  limit?: number
}) {
  const userId = useAuthStore((state) => state.user?.id)
  const [faqs, setFaqs] = useState<ApprovedFaq[]>([])
  const [loadedKey, setLoadedKey] = useState<string | null>(null)

  const requestKey = userId ? `${courseId}:${userId}` : null
  const isLoading = requestKey !== null && loadedKey !== requestKey

  useEffect(() => {
    if (!requestKey || !userId) {
      return
    }

    let cancelled = false

    const controller = new AbortController()

    void (async () => {
      try {
        const response = await listApprovedFaqs(courseId, userId, { signal: controller.signal })
        if (cancelled) {
          return
        }
        setFaqs(response.approvedFaqs)
        setLoadedKey(requestKey)
      } catch (caught) {
        if (cancelled || (caught instanceof DOMException && caught.name === 'AbortError')) {
          return
        }
        setFaqs([])
        setLoadedKey(requestKey)
      }
    })()

    return () => {
      cancelled = true
      controller.abort()
    }
  }, [courseId, requestKey, userId])

  const visible = (requestKey ? faqs : []).slice(0, limit)

  return (
    <ContextWidgetSection
      title="Approved FAQs"
      action={
        <Link
          to={supportOperationPath(serverId, courseId, 'faq-approval')}
          className="text-[11px] font-medium text-app-accent hover:text-app-accent-hover"
        >
          View all
        </Link>
      }
    >
      {!userId ? (
        <p className="text-xs text-app-muted">Sign in to view approved FAQs.</p>
      ) : isLoading ? (
        <p className="text-xs text-app-muted">Loading FAQs…</p>
      ) : visible.length === 0 ? (
        <p className="text-xs text-app-muted">No approved FAQs yet for this course.</p>
      ) : (
        <ul className="space-y-2">
          {visible.map((faq) => (
            <li key={faq.id} className="rounded-md border border-app-border bg-app-surface px-2 py-2">
              <p className="text-xs font-medium text-app-text">{faq.question}</p>
              <p className="mt-1 line-clamp-2 text-[11px] text-app-muted">{faq.answer}</p>
            </li>
          ))}
        </ul>
      )}
    </ContextWidgetSection>
  )
}
