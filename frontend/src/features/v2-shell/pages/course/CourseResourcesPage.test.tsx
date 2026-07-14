import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CourseResource, CourseResourceFilter } from '../../../resources/course-resource-types'
import type { StudyAssistantInstallPreview } from '../../../study-assistant/study-assistant-types'
import { CourseResourcesPage } from './CourseResourcesPage'

const mocks = vi.hoisted(() => ({
  fetchPresence: vi.fn(),
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

vi.mock('../../../../stores/auth-store', () => ({
  useAuthStore: (selector: (state: { user: { id: string } }) => unknown) =>
    selector({ user: { id: 'owner-1' } }),
}))

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
    course: { id: 'course-1', title: 'Algorithms' },
    courseCapabilities: { canUploadResources: true },
  }),
}))

describe('CourseResourcesPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
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

  it('searches, filters, previews, and downloads durable resources', async () => {
    const user = userEvent.setup()
    const resource = courseResource()
    mocks.resources.resources = [resource]
    mocks.resources.filteredResources = [resource]

    renderPage()

    expect(screen.getByText('Recursion notes')).toBeInTheDocument()
    expect(screen.getByText('PDF · 2.0 KB')).toBeInTheDocument()
    expect(screen.getByText('AI-approved')).toBeInTheDocument()

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

  return render(
    <QueryClientProvider client={queryClient}>
      <CourseResourcesPage />
    </QueryClientProvider>,
  )
}

function courseResource(overrides: Partial<CourseResource> = {}): CourseResource {
  return {
    id: 'resource-1',
    courseId: 'course-1',
    title: 'Recursion notes',
    fileName: 'recursion.pdf',
    contentType: 'application/pdf',
    byteSize: 2048,
    aiApproved: true,
    uploadedByUserId: 'owner-1',
    createdAt: '2026-07-13T12:00:00Z',
    ...overrides,
  }
}
