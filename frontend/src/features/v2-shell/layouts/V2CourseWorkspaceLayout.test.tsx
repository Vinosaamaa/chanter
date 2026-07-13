import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { V2CourseWorkspaceLayout } from './V2CourseWorkspaceLayout'
import { useV2CourseWorkspace } from './v2-course-workspace-context'

const navigation = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))

vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useStudyServerNavigationQuery: () => navigation.value,
}))

describe('V2CourseWorkspaceLayout', () => {
  afterEach(cleanup)

  beforeEach(() => {
    localStorage.clear()
    navigation.value = { data: undefined, isLoading: false, isError: false }
  })

  it('does not mount course children while navigation is loading', () => {
    navigation.value = { data: undefined, isLoading: true, isError: false }

    renderWorkspace()

    expect(screen.getByRole('status')).toHaveTextContent('Loading course')
    expect(screen.queryByTestId('course-probe')).not.toBeInTheDocument()
  })

  it('does not substitute demo data when the routed course is unavailable', () => {
    navigation.value = {
      data: { studyServerName: 'Spring Bootcamp Hub', canViewFullCatalog: false, courses: [] },
      isLoading: false,
      isError: false,
    }

    renderWorkspace()

    expect(screen.getByRole('heading', { name: 'Course not found' })).toBeInTheDocument()
    expect(screen.queryByTestId('course-probe')).not.toBeInTheDocument()
  })

  it('provides the routed course only after navigation resolves', () => {
    navigation.value = {
      data: {
        studyServerName: 'Spring Bootcamp Hub',
        canViewFullCatalog: true,
        capabilities: {
          owner: true,
          canTeach: true,
          canCreateCourse: true,
          canManageCommunity: true,
          canManageEvents: true,
          canManageBilling: true,
        },
        courses: [{
          id: 'course-real',
          title: 'CS 101',
          capabilities: {
            instructor: true,
            teachingAssistant: false,
            enrolled: false,
            canManageCourse: true,
            canManageQuestions: true,
            canApproveFaq: true,
            canManageTaQueue: true,
            canUploadResources: true,
            canScheduleOfficeHours: true,
            canManagePeople: true,
          },
          cohorts: [],
          channels: [],
        }],
      },
      isLoading: false,
      isError: false,
    }

    renderWorkspace()

    expect(screen.getByTestId('course-probe')).toHaveTextContent('course-real')
    expect(screen.getByRole('button', { name: 'Invite' })).toBeInTheDocument()
  })

  it('uses explicit owner and per-course instructor capabilities instead of catalog visibility', () => {
    navigation.value = {
      data: {
        studyServerName: 'Spring Bootcamp Hub',
        canViewFullCatalog: true,
        capabilities: {
          owner: false,
          canTeach: true,
          canCreateCourse: false,
          canManageCommunity: false,
          canManageEvents: false,
          canManageBilling: false,
        },
        courses: [{
          id: 'course-real',
          title: 'CS 101',
          capabilities: {
            instructor: true,
            teachingAssistant: false,
            enrolled: false,
            canManageCourse: true,
            canManageQuestions: true,
            canApproveFaq: true,
            canManageTaQueue: true,
            canUploadResources: true,
            canScheduleOfficeHours: true,
            canManagePeople: true,
          },
          cohorts: [],
          channels: [],
        }],
      },
      isLoading: false,
      isError: false,
    }

    renderWorkspace()

    expect(screen.getByTestId('owner-probe')).toHaveTextContent('learner')
    expect(screen.getByRole('button', { name: 'Invite' })).toBeInTheDocument()
  })

  it('restores and switches the selected cohort across the workspace', async () => {
    localStorage.setItem('chanter:last-cohort:server-real:course-real', 'cohort-fall')
    navigation.value = {
      data: {
        studyServerName: 'Spring Bootcamp Hub',
        canViewFullCatalog: false,
        capabilities: {
          owner: false,
          canTeach: true,
          canCreateCourse: false,
          canManageCommunity: false,
          canManageEvents: false,
          canManageBilling: false,
        },
        courses: [{
          id: 'course-real',
          title: 'CS 101',
          capabilities: {
            instructor: true,
            teachingAssistant: false,
            enrolled: false,
            canManageCourse: true,
            canManageQuestions: true,
            canApproveFaq: true,
            canManageTaQueue: true,
            canUploadResources: true,
            canScheduleOfficeHours: true,
            canManagePeople: true,
          },
          cohorts: [
            { id: 'cohort-spring', name: 'Spring 2026', capabilities: { enrolled: false, teachingAssistant: false, canManage: true } },
            { id: 'cohort-fall', name: 'Fall 2026', capabilities: { enrolled: false, teachingAssistant: false, canManage: true } },
          ],
          channels: [],
        }],
      },
      isLoading: false,
      isError: false,
    }

    renderWorkspace()

    expect(screen.getByTestId('cohort-probe')).toHaveTextContent('cohort-fall')
    await waitFor(() => expect(screen.getByTestId('location-probe')).toHaveTextContent('?cohort=cohort-fall'))
    fireEvent.click(screen.getByRole('button', { name: 'Change cohort' }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: 'Spring 2026' }))
    expect(screen.getByTestId('cohort-probe')).toHaveTextContent('cohort-spring')
    expect(localStorage.getItem('chanter:last-cohort:server-real:course-real')).toBe('cohort-spring')
  })

  it('exposes teaching-assistant actions without people-management controls', () => {
    navigation.value = {
      data: {
        studyServerName: 'Spring Bootcamp Hub',
        canViewFullCatalog: false,
        capabilities: {
          owner: false,
          canTeach: true,
          canCreateCourse: false,
          canManageCommunity: false,
          canManageEvents: false,
          canManageBilling: false,
        },
        courses: [{
          id: 'course-real',
          title: 'CS 101',
          capabilities: {
            instructor: false,
            teachingAssistant: true,
            enrolled: false,
            canManageCourse: false,
            canManageQuestions: true,
            canApproveFaq: false,
            canManageTaQueue: true,
            canUploadResources: false,
            canScheduleOfficeHours: false,
            canManagePeople: false,
          },
          cohorts: [
            { id: 'cohort-spring', name: 'Spring 2026', capabilities: { enrolled: false, teachingAssistant: true, canManage: true } },
          ],
          channels: [],
        }],
      },
      isLoading: false,
      isError: false,
    }

    renderWorkspace()

    expect(screen.getByTestId('role-probe')).toHaveTextContent('teaching-assistant')
    expect(screen.getByTestId('question-management-probe')).toHaveTextContent('can-manage-questions')
    expect(screen.queryByRole('button', { name: 'Invite' })).not.toBeInTheDocument()
  })

  it('exposes an enrolled learner without teaching controls', () => {
    navigation.value = {
      data: {
        studyServerName: 'Spring Bootcamp Hub',
        canViewFullCatalog: false,
        capabilities: {
          owner: false,
          canTeach: false,
          canCreateCourse: false,
          canManageCommunity: false,
          canManageEvents: false,
          canManageBilling: false,
        },
        courses: [{
          id: 'course-real',
          title: 'CS 101',
          capabilities: {
            instructor: false,
            teachingAssistant: false,
            enrolled: true,
            canManageCourse: false,
            canManageQuestions: false,
            canApproveFaq: false,
            canManageTaQueue: false,
            canUploadResources: false,
            canScheduleOfficeHours: false,
            canManagePeople: false,
          },
          cohorts: [
            { id: 'cohort-spring', name: 'Spring 2026', capabilities: { enrolled: true, teachingAssistant: false, canManage: false } },
          ],
          channels: [],
        }],
      },
      isLoading: false,
      isError: false,
    }

    renderWorkspace()

    expect(screen.getByTestId('role-probe')).toHaveTextContent('learner')
    expect(screen.getByTestId('question-management-probe')).toHaveTextContent('cannot-manage-questions')
    expect(screen.queryByRole('button', { name: 'Invite' })).not.toBeInTheDocument()
  })
})

function renderWorkspace() {
  render(
    <MemoryRouter initialEntries={['/app/servers/server-real/courses/course-real']}>
      <Routes>
        <Route path="/app/servers/:serverId/courses/:courseId" element={<V2CourseWorkspaceLayout />}>
          <Route index element={<CourseProbe />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

function CourseProbe() {
  const { course, courseCapabilities, isOwner, selectedCohort } = useV2CourseWorkspace()
  const location = useLocation()
  const role = courseCapabilities.instructor
    ? 'instructor'
    : courseCapabilities.teachingAssistant
      ? 'teaching-assistant'
      : 'learner'

  return <><p data-testid="course-probe">{course.id}</p><p data-testid="owner-probe">{isOwner ? 'owner' : 'learner'}</p><p data-testid="cohort-probe">{selectedCohort?.id ?? 'none'}</p><p data-testid="role-probe">{role}</p><p data-testid="question-management-probe">{courseCapabilities.canManageQuestions ? 'can-manage-questions' : 'cannot-manage-questions'}</p><p data-testid="location-probe">{location.search}</p></>
}
