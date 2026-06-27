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

  it('rejects empty learner user id', async () => {
    const { result } = renderHook(() => useCohortEnrollment('cohort-1'))

    let enrolled = true
    await act(async () => {
      enrolled = await result.current.enroll()
    })

    expect(enrolled).toBe(false)
    expect(result.current.error).toMatch(/enter the learner user id/i)
    expect(mockedEnrollLearner).not.toHaveBeenCalled()
  })

  it('rejects missing cohort id', async () => {
    const { result } = renderHook(() => useCohortEnrollment(''))

    act(() => {
      result.current.setLearnerUserId('learner-42')
    })

    let enrolled = true
    await act(async () => {
      enrolled = await result.current.enroll()
    })

    expect(enrolled).toBe(false)
    expect(result.current.error).toMatch(/select a cohort/i)
    expect(mockedEnrollLearner).not.toHaveBeenCalled()
  })

  it('enrolls trimmed learner user id', async () => {
    mockedEnrollLearner.mockResolvedValue(undefined)
    const { result } = renderHook(() => useCohortEnrollment('cohort-1'))

    act(() => {
      result.current.setLearnerUserId('  learner-42  ')
    })

    let enrolled = false
    await act(async () => {
      enrolled = await result.current.enroll()
    })

    expect(enrolled).toBe(true)
    expect(mockedEnrollLearner).toHaveBeenCalledWith('cohort-1', 'learner-42')
    expect(result.current.successMessage).toMatch(/learner enrolled/i)
  })
})
