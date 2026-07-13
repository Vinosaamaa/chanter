import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { V2CourseWorkspaceLayout } from './V2CourseWorkspaceLayout'
import { useV2CourseWorkspace } from './v2-course-workspace-context'

const navigation = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))

vi.mock('../../shell/hooks/use-shell-queries', () => ({
  useStudyServerNavigationQuery: () => navigation.value,
}))

describe('V2CourseWorkspaceLayout', () => {
  beforeEach(() => {
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
        courses: [{ id: 'course-real', title: 'CS 101', cohorts: [], channels: [] }],
      },
      isLoading: false,
      isError: false,
    }

    renderWorkspace()

    expect(screen.getByTestId('course-probe')).toHaveTextContent('course-real')
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
  const { course } = useV2CourseWorkspace()
  return <p data-testid="course-probe">{course.id}</p>
}
