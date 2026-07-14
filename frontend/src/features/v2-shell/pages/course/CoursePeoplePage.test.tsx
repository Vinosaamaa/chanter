import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CoursePeoplePage } from './CoursePeoplePage'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  workspace: {
    courseCapabilities: { canManagePeople: true },
    selectedCohort: { id: 'cohort-1', name: 'Spring 2026' },
  },
  roster: {
    query: {
      data: {
        cohortId: 'cohort-1',
        instructor: {
          userId: 'instructor-1',
          invitationId: null,
          displayName: 'Instructor Rivera',
          email: 'rivera@example.edu',
          role: 'INSTRUCTOR',
          status: 'ENROLLED',
          assignedTeachingAssistantUserId: null,
          enrolledAt: null,
        },
        teachingAssistants: [
          {
            userId: 'ta-1',
            invitationId: null,
            displayName: 'Taylor Nguyen',
            email: 'taylor@example.edu',
            role: 'TA',
            status: 'ENROLLED',
            assignedTeachingAssistantUserId: null,
            enrolledAt: null,
          },
        ],
        learners: [
          {
            userId: 'friend-1',
            invitationId: null,
            displayName: 'Real Friend',
            email: 'friend@example.edu',
            role: 'LEARNER',
            status: 'ENROLLED',
            assignedTeachingAssistantUserId: null,
            enrolledAt: '2026-07-01T00:00:00Z',
          },
          {
            userId: 'pending-1',
            invitationId: 'invitation-1',
            displayName: 'Pending Person',
            email: 'pending@example.edu',
            role: 'LEARNER',
            status: 'PENDING',
            assignedTeachingAssistantUserId: null,
            enrolledAt: '2026-07-02T00:00:00Z',
          },
        ],
        learnerCount: 1,
        teachingAssistantCount: 1,
        pendingCount: 1,
        limit: 500,
        offset: 0,
      },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    },
    invite: { mutateAsync: vi.fn(), isPending: false, error: null },
    addTa: { mutateAsync: vi.fn(), isPending: false, error: null },
    removeTa: { mutateAsync: vi.fn(), isPending: false, error: null },
    assignTa: { mutateAsync: vi.fn(), isPending: false, error: null },
    removeEnrollment: { mutateAsync: vi.fn(), isPending: false, error: null },
    cancelInvitation: { mutateAsync: vi.fn(), isPending: false, error: null },
  },
  enrollment: {
    learnerEmail: '',
    setLearnerEmail: vi.fn(),
    isSubmitting: false,
    error: null,
    successMessage: null,
    enroll: vi.fn(),
    reset: vi.fn(),
  },
  cohortInvite: { data: { inviteCode: 'invite-code' } },
}))

vi.mock('../../layouts/v2-course-workspace-context', () => ({
  useV2CourseWorkspace: () => mocks.workspace,
}))
vi.mock('../../../people/use-cohort-roster', () => ({
  useCohortRoster: () => mocks.roster,
}))
vi.mock('../../../people/use-accepted-friend-ids', () => ({
  useAcceptedFriendIds: () => ({ friendIds: new Set(['friend-1']), isLoading: false }),
}))
vi.mock('../../../onboarding/hooks/use-cohort-enrollment', () => ({
  useCohortEnrollment: () => mocks.enrollment,
}))
vi.mock('../../../onboarding/hooks/use-cohort-enrollments', () => ({
  useCohortEnrollments: () => ({ data: { enrollments: [], totalCount: 0 } }),
  useCohortInvite: () => mocks.cohortInvite,
}))
vi.mock('../../../../stores/auth-store', () => ({
  useAuthStore: (selector: (state: { user: { id: string } }) => unknown) =>
    selector({ user: { id: 'current-user' } }),
}))
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mocks.navigate }
})

describe('CoursePeoplePage', () => {
  afterEach(cleanup)
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.workspace.courseCapabilities.canManagePeople = true
  })

  it('renders only the real roster and persists per-row TA assignment', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(screen.getByText('Instructor Rivera')).toBeInTheDocument()
    expect(screen.getAllByText('Taylor Nguyen').length).toBeGreaterThan(0)
    expect(screen.getByText('Real Friend')).toBeInTheDocument()
    expect(screen.getByText('Pending Person')).toBeInTheDocument()
    expect(screen.queryByText('Dr. Alex Johnson')).not.toBeInTheDocument()
    expect(screen.getByText('1 learner · 1 TA · 1 pending')).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Assigned TA for Real Friend'), 'ta-1')
    expect(mocks.roster.assignTa.mutateAsync).toHaveBeenCalledWith({
      learnerUserIds: ['friend-1'],
      teachingAssistantUserId: 'ta-1',
    })
  })

  it('opens a direct message only for an accepted friend', async () => {
    const user = userEvent.setup()
    mocks.workspace.courseCapabilities.canManagePeople = false
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Message Real Friend' }))
    expect(mocks.navigate).toHaveBeenCalledWith('/app/friends?friend=friend-1')
    expect(screen.getByRole('button', { name: 'Message Pending Person' })).toBeDisabled()
  })

  it('reports a failed teaching-assistant removal to the manager', async () => {
    const user = userEvent.setup()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    mocks.roster.removeTa.mutateAsync.mockRejectedValueOnce(new Error('service unavailable'))
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Remove Taylor Nguyen as teaching assistant' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('service unavailable')
    confirm.mockRestore()
  })
})

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <CoursePeoplePage />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}
