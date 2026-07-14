import { useCallback, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import { formatUserFacingApiError } from '../../../lib/format-api-error'

import { enrollLearner } from '../onboarding-api'

type UseCohortEnrollmentResult = {
  learnerEmail: string
  setLearnerEmail: (value: string) => void
  isSubmitting: boolean
  error: string | null
  successMessage: string | null
  enroll: () => Promise<boolean>
  reset: () => void
}

function enrollmentErrorMessage(caught: unknown): string {
  if (caught instanceof ApiError && caught.status === 403) {
    return 'Only course instructors can enroll learners in this cohort.'
  }
  return formatUserFacingApiError(caught, 'Unable to enroll learner.')
}

export function useCohortEnrollment(cohortId: string): UseCohortEnrollmentResult {
  const [learnerEmail, setLearnerEmail] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const reset = useCallback(() => {
    setLearnerEmail('')
    setError(null)
    setSuccessMessage(null)
  }, [])

  const enroll = useCallback(async () => {
    if (isSubmitting) {
      return false
    }

    if (!cohortId) {
      setSuccessMessage(null)
      setError('Select a cohort before enrolling a learner.')
      return false
    }

    const trimmed = learnerEmail.trim()
    if (!trimmed) {
      setSuccessMessage(null)
      setError('Enter the learner email to enroll.')
      return false
    }

    setIsSubmitting(true)
    setError(null)
    setSuccessMessage(null)

    try {
      await enrollLearner(cohortId, trimmed)
      setSuccessMessage('Learner enrolled. They will see this course under My courses.')
      return true
    } catch (caught) {
      setError(enrollmentErrorMessage(caught))
      return false
    } finally {
      setIsSubmitting(false)
    }
  }, [cohortId, isSubmitting, learnerEmail])

  return {
    learnerEmail,
    setLearnerEmail,
    isSubmitting,
    error,
    successMessage,
    enroll,
    reset,
  }
}
