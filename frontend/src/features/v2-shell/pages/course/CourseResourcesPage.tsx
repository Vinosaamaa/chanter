import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  Check,
  Download,
  Eye,
  FileText,
  Folder,
  LoaderCircle,
  Search,
  Sparkles,
  X,
} from 'lucide-react'

import { useAuthStore } from '../../../../stores/auth-store'
import { fetchStudyAssistantPresence } from '../../../questions/questions-api'
import {
  formatByteSize,
  isPdfResource,
  resourceFileKind,
  resourceKindLabel,
} from '../../../resources/course-resource-format'
import type {
  CourseResource,
  CourseResourceFilter,
} from '../../../resources/course-resource-types'
import { useCourseResourcesChannel } from '../../../shell/hooks/use-course-resources-channel'
import { StudyAssistantInstallDialog } from '../../../study-assistant/components/StudyAssistantInstallDialog'
import {
  studyAssistantPresenceQueryKey,
  useStudyAssistantInstallFlow,
} from '../../../study-assistant/hooks/use-study-assistant-install'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

const filters: { id: CourseResourceFilter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'slides', label: 'Slides' },
  { id: 'recordings', label: 'Recordings' },
  { id: 'assignments', label: 'Assignments' },
]

const MAX_RESOURCE_FILE_BYTES = 10 * 1024 * 1024

export function CourseResourcesPage() {
  const { course, serverId, courseCapabilities } = useV2CourseWorkspace()
  const userId = useAuthStore((state) => state.user?.id)
  const resources = useCourseResourcesChannel(course.id)
  const install = useStudyAssistantInstallFlow({
    studyServerId: serverId,
    instructorUserId: userId,
  })
  const assistantQuery = useQuery({
    queryKey: studyAssistantPresenceQueryKey(serverId, userId),
    queryFn: () => fetchStudyAssistantPresence(serverId),
    enabled: Boolean(serverId && userId && courseCapabilities.canUploadResources),
  })
  const [uploadOpen, setUploadOpen] = useState(false)
  const canManageResources = courseCapabilities.canUploadResources && resources.canUpload
  const selectedResourceId = new URLSearchParams(window.location.search).get('resource')

  let assistantControl = null
  if (canManageResources) {
    if (assistantQuery.isLoading) {
      assistantControl = <span className="resource-assistant-status">Checking AI status…</span>
    } else if (assistantQuery.isError) {
      assistantControl = (
        <span className="resource-assistant-status error">AI status unavailable</span>
      )
    } else if (assistantQuery.data?.installed) {
      assistantControl = (
        <span className="resource-assistant-status active">
          <Sparkles />
          AI Study Assistant · Active
        </span>
      )
    } else {
      assistantControl = (
        <button
          type="button"
          className="v2-outline-button"
          onClick={() => void install.openInstallDialog()}
          disabled={install.isOpening}
        >
          {install.isOpening ? (
            'Loading install options…'
          ) : (
            <>
              <Sparkles />
              Install AI Study Assistant
            </>
          )}
        </button>
      )
    }
  }

  return (
    <div className="resources-page">
      <div className="resources-toolbar">
        <label>
          <Search />
          <input
            aria-label="Search resources"
            value={resources.searchQuery}
            onChange={(event) => resources.setSearchQuery(event.target.value)}
            placeholder="Search resources…"
            disabled={resources.isLoading || !resources.canView}
          />
        </label>
        <div className="resource-filters">
          {filters.map((filter) => (
            <button
              type="button"
              key={filter.id}
              className={resources.activeFilter === filter.id ? 'active' : undefined}
              onClick={() => resources.setActiveFilter(filter.id)}
              disabled={resources.isLoading || !resources.canView}
            >
              {filter.label}
            </button>
          ))}
        </div>
        {canManageResources ? (
          <div className="resource-owner-actions">
            <button
              type="button"
              className="v2-primary-button"
              onClick={() => setUploadOpen(true)}
              disabled={resources.isUploading}
            >
              {resources.isUploading ? 'Uploading…' : 'Upload'}
            </button>
            {assistantControl}
          </div>
        ) : null}
      </div>

      {resources.error && !uploadOpen ? (
        <p className="inline-error" role="alert">
          {resources.error}
        </p>
      ) : null}
      {resources.uploadSuccess ? (
        <p className="inline-success" role="status">
          {resources.uploadSuccess}
        </p>
      ) : null}
      {install.installError && !install.isDialogOpen ? (
        <p className="inline-error" role="alert">
          {install.installError}
        </p>
      ) : null}

      <div className="resource-week-list">
        {resources.isLoading ? <div className="resource-empty">Loading resources…</div> : null}
        {!resources.isLoading &&
        !resources.error &&
        resources.canView &&
        resources.filteredResources.length > 0 ? (
          <section className="resource-week">
            <header>
              <Folder />
              <h2>Course resources</h2>
            </header>
            <div className="resource-rows">
              {resources.filteredResources.map((resource) => (
                <LiveResourceRow
                  key={resource.id}
                  resource={resource}
                  highlighted={resource.id === selectedResourceId}
                  isDownloading={resources.downloadingResourceId === resource.id}
                  onPreview={() => void resources.previewResource(resource)}
                  onDownload={() => void resources.downloadResource(resource)}
                />
              ))}
            </div>
          </section>
        ) : null}
        {!resources.isLoading &&
        !resources.error &&
        resources.canView &&
        resources.resources.length === 0 ? (
          <div className="resource-empty">No resources uploaded yet.</div>
        ) : null}
        {!resources.isLoading &&
        !resources.error &&
        resources.canView &&
        resources.resources.length > 0 &&
        resources.filteredResources.length === 0 ? (
          <div className="resource-empty">No resources match your search.</div>
        ) : null}
      </div>

      {uploadOpen ? (
        <UploadResourceDialog
          isUploading={resources.isUploading}
          uploadError={resources.error}
          onClose={() => setUploadOpen(false)}
          onUpload={resources.uploadResource}
        />
      ) : null}
      {install.isDialogOpen && install.preview ? (
        <StudyAssistantInstallDialog
          preview={install.preview}
          selectedKeys={install.selectedKeys}
          installError={install.installError}
          isInstalling={install.isInstalling}
          onToggleKey={install.toggleGrantKey}
          onCancel={install.closeDialog}
          onConfirm={install.confirmInstall}
        />
      ) : null}
    </div>
  )
}

function UploadResourceDialog({
  isUploading,
  uploadError,
  onClose,
  onUpload,
}: {
  isUploading: boolean
  uploadError: string | null
  onClose: () => void
  onUpload: (file: File, options: { title?: string; aiApproved: boolean }) => Promise<boolean>
}) {
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [aiApproved, setAiApproved] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)
  const previouslyFocusedRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    previouslyFocusedRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    formRef.current?.querySelector<HTMLInputElement>('input[type="file"]')?.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (!isUploading) {
          event.preventDefault()
          onClose()
        }
        return
      }

      if (event.key !== 'Tab' || !formRef.current) return

      const focusable = Array.from(
        formRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
        ),
      )
      if (focusable.length === 0) return

      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previouslyFocusedRef.current?.focus()
    }
  }, [isUploading, onClose])

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!file) return

    const uploaded = await onUpload(file, {
      title: title.trim() || undefined,
      aiApproved,
    })
    if (uploaded) onClose()
  }

  const selectFile = (selectedFile: File | null) => {
    if (selectedFile && selectedFile.size > MAX_RESOURCE_FILE_BYTES) {
      setFile(null)
      setFileError('Files must be 10 MB or smaller.')
      return
    }

    setFile(selectedFile)
    setFileError(null)
  }

  return (
    <div className="v2-modal-backdrop" role="presentation">
      <form
        ref={formRef}
        className="resource-upload-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="resource-upload-title"
        onSubmit={(event) => void submit(event)}
      >
        <button
          type="button"
          className="modal-close"
          onClick={onClose}
          disabled={isUploading}
          aria-label="Close"
        >
          <X />
        </button>
        <h2 id="resource-upload-title">Upload course resource</h2>
        <label htmlFor="resource-upload-file">
          <span>Resource file</span>
          <input
            id="resource-upload-file"
            type="file"
            aria-label="Resource file"
            aria-describedby="resource-upload-file-help"
            onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
          />
          <small id="resource-upload-file-help">Maximum file size: 10 MB</small>
        </label>
        {fileError ? (
          <p className="modal-error" role="alert">
            {fileError}
          </p>
        ) : null}
        {!fileError && uploadError ? (
          <p className="modal-error" role="alert">
            {uploadError}
          </p>
        ) : null}
        <label htmlFor="resource-upload-title-input">
          <span>Resource title</span>
          <input
            id="resource-upload-title-input"
            aria-label="Resource title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder={file?.name ?? 'Optional title'}
          />
        </label>
        <label className="resource-ai-approval" htmlFor="resource-upload-ai-approved">
          <input
            id="resource-upload-ai-approved"
            type="checkbox"
            aria-label="Allow AI Study Assistant to use this resource"
            checked={aiApproved}
            onChange={(event) => setAiApproved(event.target.checked)}
          />
          <span>Allow AI Study Assistant to use this resource</span>
        </label>
        <footer>
          <button
            type="button"
            className="v2-outline-button"
            onClick={onClose}
            disabled={isUploading}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="v2-primary-button"
            disabled={!file || isUploading}
          >
            {isUploading ? 'Uploading…' : 'Upload resource'}
          </button>
        </footer>
      </form>
    </div>
  )
}

function LiveResourceRow({
  resource,
  highlighted,
  isDownloading,
  onPreview,
  onDownload,
}: {
  resource: CourseResource
  highlighted: boolean
  isDownloading: boolean
  onPreview: () => void
  onDownload: () => void
}) {
  const kind = resourceFileKind(resource)
  const rowRef = useRef<HTMLElement>(null)

  useEffect(() => {
    if (!highlighted || !rowRef.current) return
    rowRef.current.scrollIntoView?.({ block: 'center' })
    rowRef.current.focus()
  }, [highlighted])

  return (
    <article
      ref={rowRef}
      className={`resource-row ${highlighted ? 'highlighted' : ''}`}
      aria-current={highlighted ? 'true' : undefined}
      tabIndex={highlighted ? -1 : undefined}
    >
      <span className={`resource-type-icon ${kind}`}>
        <FileText />
      </span>
      <div>
        <strong>{resource.title}</strong>
        <p>
          {resourceKindLabel(kind)} · {formatByteSize(resource.byteSize)}
        </p>
      </div>
      <span className="resource-statuses">
        {resource.aiApproved ? (
          <b>
            <Check />
            AI-approved
          </b>
        ) : null}
      </span>
      <span className="resource-row-actions">
        {isPdfResource(resource) ? (
          <button
            type="button"
            onClick={onPreview}
            disabled={isDownloading}
            aria-label={`Open ${resource.title}`}
            title="Open"
          >
            <Eye />
          </button>
        ) : null}
        <button
          type="button"
          onClick={onDownload}
          disabled={isDownloading}
          aria-label={`Download ${resource.title}`}
          title="Download"
        >
          {isDownloading ? <LoaderCircle className="resource-action-spinner" /> : <Download />}
        </button>
      </span>
    </article>
  )
}
