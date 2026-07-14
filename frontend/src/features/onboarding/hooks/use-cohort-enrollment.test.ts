import { renderHook, act } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { enrollLearner } from '../onboarding-api'
import { useCohortEnrollment } from './use-cohort-enrollment'

vi.mock('../onboarding-api', () => ({
  enrollLearner: vi.fn(),
}))

const mockedEnrollLearner = vi.mocked(enrollLearner)

describe('useCohortEnrollment', () => {
  beforeEach(() => {
    mockedEnrollLearner.mockReset()
  })

  it('rejects empty learner email', async () => {
    const { result } = renderHook(() => useCohortEnrollment('cohort-1'))

    let enrolled = true
    await act(async () => {
      enrolled = await result.current.enroll()
    })

    expect(enrolled).toBe(false)
    expect(result.current.error).toMatch(/enter the learner email/i)
    expect(mockedEnrollLearner).not.toHaveBeenCalled()
  })

  it('rejects missing cohort id', async () => {
    const { result } = renderHook(() => useCohortEnrollment(''))

    act(() => {
      result.current.setLearnerEmail('learner@example.edu')
    })

    let enrolled = true
    await act(async () => {
      enrolled = await result.current.enroll()
    })

    expect(enrolled).toBe(false)
    expect(result.current.error).toMatch(/select a cohort/i)
    expect(mockedEnrollLearner).not.toHaveBeenCalled()
  })

  it('enrolls trimmed learner email', async () => {
    mockedEnrollLearner.mockResolvedValue(undefined)
    const { result } = renderHook(() => useCohortEnrollment('cohort-1'))

    act(() => {
      result.current.setLearnerEmail('  learner@example.edu  ')
    })

    let enrolled = false
    await act(async () => {
      enrolled = await result.current.enroll()
    })

    expect(enrolled).toBe(true)
    expect(mockedEnrollLearner).toHaveBeenCalledWith('cohort-1', 'learner@example.edu')
    expect(result.current.successMessage).toMatch(/learner enrolled/i)
  })
})
