import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CourseResource, CourseResourceFilter } from '../../../resources/course-resource-types'
import type { StudyAssistantInstallPreview } from '../../../study-assistant/study-assistant-types'
import { CourseResourcesPage } from './CourseResourcesPage'
import { useAuthStore } from '../../../../stores/auth-store'

const mocks = vi.hoisted(() => ({
  courseId: 'course-1',
  fetchPresence: vi.fn(),
  deleteResource: vi.fn(),
  resources: {
    resources: [] as CourseResource[],
    filteredResources: [] as CourseResource[],
    isLoading: false,
    accessDenied: false,
    canUpload: true,
    canView: true,
    error: null as string | null,
    uploadSuccess: null as string | null,
    searchQuery: '',
    setSearchQuery: vi.fn(),
    activeFilter: 'all' as CourseResourceFilter,
    setActiveFilter: vi.fn(),
    uploadResource: vi.fn(),
    isUploading: false,
    downloadResource: vi.fn(),
    previewResource: vi.fn(),
    downloadingResourceId: null,
    aiApprovedCount: 0,
    retryIngestion: vi.fn(),
    retryingResourceId: null as string | null,
  },
  install: {
    preview: null as StudyAssistantInstallPreview | null,
    selectedKeys: new Set<string>(),
    installError: null as string | null,
    isDialogOpen: false,
    isOpening: false,
    isInstalling: false,
    openInstallDialog: vi.fn(),
    closeDialog: vi.fn(),
    toggleGrantKey: vi.fn(),
    confirmInstall: vi.fn(),
  },
}))

vi.mock('../../../resources/course-resources-api', () => ({ deleteCourseResource: mocks.deleteResource }))

vi.mock('../../../questions/questions-api', () => ({
  fetchStudyAssistantPresence: mocks.fetchPresence,
}))

vi.mock('../../../shell/hooks/use-course-resources-channel', () => ({
  useCourseResourcesChannel: () => mocks.resources,
}))

vi.mock('../../../study-assistant/hooks/use-study-assistant-install', () => ({
  studyAssistantPresenceQueryKey: (serverId: string, userId: string) => [
    'study-assistant-presence',
    serverId,
    userId,
  ],
  useStudyAssistantInstallFlow: () => mocks.install,
}))

vi.mock('../../layouts/v2-course-workspace-context', () => ({
  useV2CourseWorkspace: () => ({
    serverId: 'server-1',
    course: { id: mocks.courseId, title: 'Algorithms' },
    courseCapabilities: { canUploadResources: true },
  }),
}))

describe('CourseResourcesPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.courseId = 'course-1'
    useAuthStore.getState().setSession({ accessToken: 'owner-token', expiresInSeconds: 900, user: { id: 'owner-1', email: 'owner@example.test', displayName: 'Owner' } })
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function (this: HTMLDialogElement) { this.open = true } })
    Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function (this: HTMLDialogElement) { this.open = false } })
    window.history.replaceState({}, '', '/')
    mocks.resources.resources = []
    mocks.resources.filteredResources = []
    mocks.resources.searchQuery = ''
    mocks.resources.activeFilter = 'all'
    mocks.resources.isLoading = false
    mocks.resources.accessDenied = false
    mocks.resources.canView = true
    mocks.resources.canUpload = true
    mocks.resources.isUploading = false
    mocks.resources.error = null
    mocks.resources.uploadResource.mockResolvedValue(true)
    mocks.install.preview = null
    mocks.install.selectedKeys = new Set<string>()
    mocks.install.installError = null
    mocks.install.isDialogOpen = false
    mocks.install.isOpening = false
    mocks.install.isInstalling = false
    mocks.fetchPresence.mockResolvedValue({
      studyServerId: 'server-1',
      installed: false,
      grants: [],
    })
  })

  it('shows the durable empty state without demo resources', () => {
    renderPage()

    expect(screen.getByText('No resources uploaded yet.')).toBeInTheDocument()
    expect(screen.queryByText(/Lecture 1/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Week 1/)).not.toBeInTheDocument()
  })

  it('requires confirmation and opens pending file-deletion progress', async () => {
    const resource = courseResource()
    mocks.resources.resources = [resource]; mocks.resources.filteredResources = [resource]
    const jobId = 'b633c892-6762-40ec-a945-b042957a052b'
    mocks.deleteResource.mockResolvedValue({ jobId, targetId: resource.id, state: 'PENDING' })
    renderPage()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Delete Recursion notes' }))
    expect(mocks.deleteResource).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Delete course file' }))
    await waitFor(() => expect(screen.getByTestId('current-path')).toHaveTextContent(`/app/deletions/${jobId}`))
    expect(mocks.deleteResource).toHaveBeenCalledWith(resource.id, expect.any(AbortSignal))
    expect(screen.queryByText('Deletion completed')).not.toBeInTheDocument()
  })

  it.each(['course', 'account'] as const)('discards a deletion dialog across a %s change and return', async change => {
    const resource = courseResource()
    mocks.resources.resources = [resource]; mocks.resources.filteredResources = [resource]
    const view = renderPage()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Delete Recursion notes' }))
    expect(screen.getByRole('dialog')).toBeVisible()
    if (change === 'course') mocks.courseId = 'course-2'
    else {
      mocks.resources.isLoading = true
      await act(async () => useAuthStore.getState().setSession({ accessToken: 'other', expiresInSeconds: 900, user: { id: 'other', email: 'other@example.test', displayName: 'Other' } }))
    }
    view.rerenderPage()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    mocks.courseId = 'course-1'; mocks.resources.isLoading = false
    view.rerenderPage()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mocks.deleteResource).not.toHaveBeenCalled()
  })

  it('does not expose file deletion to a learner', () => {
    const resource = courseResource()
    mocks.resources.resources = [resource]; mocks.resources.filteredResources = [resource]
    mocks.resources.canUpload = false
    renderPage()
    expect(screen.queryByRole('button', { name: 'Delete Recursion notes' })).not.toBeInTheDocument()
  })

  it('searches, filters, previews, and downloads durable resources', async () => {
    const user = userEvent.setup()
    const resource = courseResource()
    mocks.resources.resources = [resource]
    mocks.resources.filteredResources = [resource]

    renderPage()

    expect(screen.getByText('Recursion notes')).toBeInTheDocument()
    expect(screen.getByText('PDF · 2.0 KB')).toBeInTheDocument()
    expect(screen.getByText('AI ready')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('Search resources…'), {
      target: { value: 'recursion' },
    })
    expect(mocks.resources.setSearchQuery).toHaveBeenCalledWith('recursion')

    await user.click(screen.getByRole('button', { name: 'Slides' }))
    expect(mocks.resources.setActiveFilter).toHaveBeenCalledWith('slides')

    await user.click(screen.getByRole('button', { name: 'Open Recursion notes' }))
    expect(mocks.resources.previewResource).toHaveBeenCalledWith(resource)

    await user.click(screen.getByRole('button', { name: 'Download Recursion notes' }))
    expect(mocks.resources.downloadResource).toHaveBeenCalledWith(resource)
  })

  it('highlights the exact resource selected by a citation deep link', () => {
    const resource = courseResource()
    mocks.resources.resources = [resource]
    mocks.resources.filteredResources = [resource]
    window.history.replaceState({}, '', '/resources?resource=resource-1')

    renderPage()

    expect(screen.getByText('Recursion notes').closest('article')).toHaveClass('highlighted')
  })

  it('explains scanned files and lets instructors retry only failed AI preparation', async () => {
    const user = userEvent.setup()
    const scanned = courseResource({ id: 'scan', title: 'Scanned worksheet', ingestionStatus: 'OCR_REQUIRED' })
    const failed = courseResource({ id: 'failed', title: 'Lecture notes', ingestionStatus: 'FAILED' })
    mocks.resources.resources = [scanned, failed]
    mocks.resources.filteredResources = [scanned, failed]
    renderPage()
    expect(screen.getByText('Text needed')).toBeInTheDocument()
    expect(screen.getByText('Upload a version with selectable text. OCR is not available.')).toBeInTheDocument()
    expect(screen.queryByText('AI ready')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download Scanned worksheet' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Retry AI preparation for Lecture notes' }))
    expect(mocks.resources.retryIngestion).toHaveBeenCalledWith(failed)
    expect(screen.queryByRole('button', { name: 'Retry AI preparation for Scanned worksheet' })).not.toBeInTheDocument()
  })

  it('does not offer instructor retry to learners or download while scanning', () => {
    mocks.resources.canUpload = false
    const resource = courseResource({ status: 'PROCESSING', ingestionStatus: 'FAILED' })
    mocks.resources.resources = [resource]
    mocks.resources.filteredResources = [resource]
    renderPage()
    expect(screen.getByText('Checking file')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download Recursion notes' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /Retry AI preparation/ })).not.toBeInTheDocument()
  })

  it('uploads the selected file with explicit title and AI approval', async () => {
    const user = userEvent.setup()
    const file = new File(['recursion'], 'recursion.txt', { type: 'text/plain' })

    renderPage()

    await user.click(screen.getByRole('button', { name: 'Upload' }))
    expect(screen.getByRole('dialog', { name: 'Upload course resource' })).toBeInTheDocument()

    await user.upload(screen.getByLabelText('Resource file'), file)
    await user.type(screen.getByLabelText('Resource title'), 'Recursion worksheet')
    await user.click(screen.getByLabelText('Allow AI Study Assistant to use this resource'))
    await user.click(screen.getByRole('button', { name: 'Upload resource' }))

    await waitFor(() =>
      expect(mocks.resources.uploadResource).toHaveBeenCalledWith(file, {
        title: 'Recursion worksheet',
        aiApproved: true,
      }),
    )
    expect(screen.queryByRole('dialog', { name: 'Upload course resource' })).not.toBeInTheDocument()
  })

  it('rejects files over the backend upload limit before submission', async () => {
    const user = userEvent.setup()
    const file = new File(['oversized'], 'oversized.pdf', { type: 'application/pdf' })
    Object.defineProperty(file, 'size', { value: 10 * 1024 * 1024 + 1 })

    renderPage()
    await user.click(screen.getByRole('button', { name: 'Upload' }))
    await user.upload(screen.getByLabelText('Resource file'), file)

    expect(screen.getByRole('alert')).toHaveTextContent('Files must be 10 MB or smaller.')
    expect(screen.getByRole('button', { name: 'Upload resource' })).toBeDisabled()
    expect(mocks.resources.uploadResource).not.toHaveBeenCalled()
  })

  it('keeps upload errors visible inside the open dialog', async () => {
    const user = userEvent.setup()
    mocks.resources.error = 'The resource upload failed.'

    renderPage()
    await user.click(screen.getByRole('button', { name: 'Upload' }))

    const dialog = screen.getByRole('dialog', { name: 'Upload course resource' })
    expect(within(dialog).getByRole('alert')).toHaveTextContent('The resource upload failed.')
  })

  it('closes the upload dialog with Escape when no upload is running', async () => {
    const user = userEvent.setup()

    renderPage()
    await user.click(screen.getByRole('button', { name: 'Upload' }))
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'Upload course resource' })).not.toBeInTheDocument()
  })

  it('distinguishes loading, denied, and failed requests from an empty course', () => {
    const staleResource = courseResource({ title: 'Previous course notes' })
    mocks.resources.resources = [staleResource]
    mocks.resources.filteredResources = [staleResource]
    mocks.resources.isLoading = true
    renderPage()
    expect(screen.getByText('Loading resources…')).toBeInTheDocument()
    expect(screen.queryByText('Previous course notes')).not.toBeInTheDocument()
    expect(screen.queryByText('No resources uploaded yet.')).not.toBeInTheDocument()
    cleanup()

    mocks.resources.isLoading = false
    mocks.resources.accessDenied = true
    mocks.resources.canView = false
    mocks.resources.error = 'Course Resource access requires enrollment.'
    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Course Resource access requires enrollment.',
    )
    cleanup()

    mocks.resources.accessDenied = false
    mocks.resources.canView = true
    mocks.resources.error = 'Could not load course resources.'
    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load course resources.')
    expect(screen.queryByText('No resources uploaded yet.')).not.toBeInTheDocument()
  })

  it('shows backend-derived active state without a duplicate install action', async () => {
    mocks.fetchPresence.mockResolvedValue({
      studyServerId: 'server-1',
      installed: true,
      grants: [{ grantType: 'COURSE', grantTargetId: 'course-1' }],
    })

    renderPage()

    expect(await screen.findByText('AI Study Assistant · Active')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Install AI Study Assistant' }),
    ).not.toBeInTheDocument()
  })

  it('uses real install candidates and selected grant checkboxes', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(
      await screen.findByRole('button', { name: 'Install AI Study Assistant' }),
    )
    expect(mocks.install.openInstallDialog).toHaveBeenCalledOnce()
    cleanup()

    mocks.install.preview = {
      studyServerId: 'server-1',
      alreadyInstalled: false,
      candidates: {
        studyServerId: 'server-1',
        studyServerChannels: [{ id: 'channel-general', name: 'general', kind: 'TEXT' }],
        courses: [
          {
            id: 'course-1',
            title: 'Algorithms',
            cohorts: [],
            channels: [{ id: 'channel-questions', name: 'questions', kind: 'QUESTIONS' }],
          },
        ],
      },
      courseResources: [
        {
          id: 'resource-1',
          courseId: 'course-1',
          title: 'Recursion notes',
          fileName: 'recursion.pdf',
          aiApproved: true,
        },
      ],
    }
    mocks.install.selectedKeys = new Set(['COURSE:course-1'])
    mocks.install.isDialogOpen = true
    renderPage()

    expect(
      screen.getByRole('dialog', { name: 'Install AI Study Assistant' }),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('checkbox', { name: 'Algorithms' }))
    expect(mocks.install.toggleGrantKey).toHaveBeenCalledWith('COURSE:course-1', false)

    await user.click(screen.getByRole('button', { name: 'Confirm install' }))
    expect(mocks.install.confirmInstall).toHaveBeenCalledOnce()
  })
})

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  const tree = () => (
    <MemoryRouter><QueryClientProvider client={queryClient}>
      <CourseResourcesPage />
      <CurrentPath />
    </QueryClientProvider></MemoryRouter>
  )
  const view = render(tree())
  return { ...view, rerenderPage: () => view.rerender(tree()) }
}

function CurrentPath() { return <output data-testid="current-path">{useLocation().pathname}</output> }

function courseResource(overrides: Partial<CourseResource> = {}): CourseResource {
  return {
    id: 'resource-1',
    courseId: 'course-1',
    title: 'Recursion notes',
    fileName: 'recursion.pdf',
    contentType: 'application/pdf',
    byteSize: 2048,
    aiApproved: true,
    status: 'AVAILABLE',
    ingestionStatus: 'READY',
    uploadedByUserId: 'owner-1',
    createdAt: '2026-07-13T12:00:00Z',
    ...overrides,
  }
}
