import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { StudyAssistantInstallDialog } from './StudyAssistantInstallDialog'
import type { StudyAssistantInstallPreview } from '../study-assistant-types'

const preview: StudyAssistantInstallPreview = {
  studyServerId: 'server-1',
  alreadyInstalled: false,
  candidates: {
    studyServerId: 'server-1',
    studyServerChannels: [{ id: 'ss-ch-1', name: 'lobby', kind: 'TEXT' }],
    courses: [
      {
        id: 'course-1',
        title: 'Spring Boot',
        cohorts: [{ id: 'cohort-1', name: 'March 2026' }],
        channels: [{ id: 'course-ch-1', name: 'questions', kind: 'TEXT' }],
      },
    ],
  },
  courseResources: [
    {
      id: 'resource-1',
      courseId: 'course-1',
      title: 'Week 1 Slides',
      fileName: 'week-1.pdf',
      aiApproved: true,
    },
  ],
}

describe('StudyAssistantInstallDialog', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders grant tree checkboxes and confirm install action', () => {
    const selectedKeys = new Set([
      'STUDY_SERVER_CHANNEL:ss-ch-1',
      'COURSE:course-1',
      'COHORT:cohort-1',
      'COURSE_CHANNEL:course-ch-1',
      'COURSE_RESOURCE:resource-1',
    ])

    render(
      <StudyAssistantInstallDialog
        preview={preview}
        selectedKeys={selectedKeys}
        installError={null}
        isInstalling={false}
        onToggleKey={vi.fn()}
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )

    expect(screen.getByRole('dialog', { name: /install ai study assistant/i })).toBeInTheDocument()
    expect(screen.getByLabelText('#lobby')).toBeChecked()
    expect(screen.getByLabelText('Spring Boot')).toBeChecked()
    expect(screen.getByLabelText('#questions')).toBeChecked()
    expect(screen.getByLabelText('March 2026')).toBeChecked()
    expect(screen.getByLabelText(/week 1 slides/i)).toBeChecked()
    expect(screen.getByRole('button', { name: /confirm install/i })).toBeEnabled()
  })

  it('disables confirm when nothing is selected', () => {
    render(
      <StudyAssistantInstallDialog
        preview={preview}
        selectedKeys={new Set()}
        installError={null}
        isInstalling={false}
        onToggleKey={vi.fn()}
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: /confirm install/i })).toBeDisabled()
  })

  it('shows already-installed state without confirm action', async () => {
    const onConfirm = vi.fn()

    render(
      <StudyAssistantInstallDialog
        preview={{ ...preview, alreadyInstalled: true }}
        selectedKeys={new Set()}
        installError={null}
        isInstalling={false}
        onToggleKey={vi.fn()}
        onCancel={vi.fn()}
        onConfirm={onConfirm}
      />,
    )

    expect(screen.getByText(/already installed in this study server/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /confirm install/i })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: /confirm install/i }))
    expect(onConfirm).not.toHaveBeenCalled()
  })
})
