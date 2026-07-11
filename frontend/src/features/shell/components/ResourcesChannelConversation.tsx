import { type ChangeEvent, useRef, useState, type ReactNode } from 'react'

import type { CourseResource, CourseResourceFilter } from '../../resources/course-resource-types'
import {
  formatByteSize,
  isPdfResource,
  resourceFileKind,
  resourceKindLabel,
} from '../../resources/course-resource-format'
import { useCourseResourcesChannel } from '../hooks/use-course-resources-channel'
import { useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import type { CourseChannelContext } from '../shell-routes'
import { findCourseChannelContext } from '../shell-routes'

const FILTER_OPTIONS: { id: CourseResourceFilter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'pdf', label: 'PDF' },
  { id: 'slides', label: 'Slides' },
  { id: 'assignments', label: 'Assignments' },
  { id: 'other', label: 'Other' },
]

type ResourcesChannelConversationProps = {
  channelContext: CourseChannelContext
}

export function ResourcesChannelConversation({ channelContext }: ResourcesChannelConversationProps) {
  const resources = useCourseResourcesChannel(channelContext.course.id)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploadTitle, setUploadTitle] = useState('')
  const [aiApproved, setAiApproved] = useState(false)

  const onChooseFile = () => {
    fileInputRef.current?.click()
  }

  const onFileSelected = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      return
    }

    void resources.uploadResource(file, {
      title: uploadTitle.trim() || file.name,
      aiApproved,
    })
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <header className="border-b border-app-border px-4 py-3">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
              Course resources
            </p>
            <h2 className="mt-1 text-base font-semibold text-app-text">
              #{channelContext.channel.name}
            </h2>
            <p className="mt-1 text-sm text-app-muted">
              All course resources including slides, assignments, and reading materials.
            </p>
          </div>

          <AiApprovedBanner approvedCount={resources.aiApprovedCount} />
        </div>

        <div className="mt-4 flex flex-col gap-3 lg:flex-row lg:items-center">
          <label className="relative min-w-0 flex-1">
            <span className="sr-only">Search resources</span>
            <input
              type="search"
              value={resources.searchQuery}
              onChange={(event) => resources.setSearchQuery(event.target.value)}
              placeholder="Search resources…"
              className="w-full rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text outline-none ring-app-accent focus:ring-2"
              disabled={!resources.canView}
            />
          </label>

          {resources.canUpload ? (
            <div className="flex flex-wrap items-center gap-2">
              <input
                type="text"
                value={uploadTitle}
                onChange={(event) => setUploadTitle(event.target.value)}
                placeholder="Title (optional)"
                className="min-w-[10rem] flex-1 rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm text-app-text outline-none ring-app-accent focus:ring-2 lg:flex-none"
              />
              <label className="flex items-center gap-2 rounded-lg border border-app-border px-3 py-2 text-xs text-app-muted">
                <input
                  type="checkbox"
                  checked={aiApproved}
                  onChange={(event) => setAiApproved(event.target.checked)}
                />
                AI-approved
              </label>
              <button
                type="button"
                className="rounded-lg bg-app-accent px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                onClick={onChooseFile}
                disabled={resources.isUploading}
              >
                {resources.isUploading ? 'Uploading…' : 'Upload resource'}
              </button>
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                onChange={onFileSelected}
              />
            </div>
          ) : null}
        </div>

        {resources.canView ? (
          <div className="mt-3 flex flex-wrap gap-2">
            {FILTER_OPTIONS.map((option) => (
              <button
                key={option.id}
                type="button"
                className={`rounded-full border px-3 py-1 text-xs font-semibold ${
                  resources.activeFilter === option.id
                    ? 'border-app-accent bg-app-accent text-white'
                    : 'border-app-border text-app-muted hover:bg-app-surface'
                }`}
                onClick={() => resources.setActiveFilter(option.id)}
              >
                {option.label}
              </button>
            ))}
          </div>
        ) : null}
      </header>

      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto p-4">
        {resources.isLoading ? (
          <p className="text-sm text-app-muted">Loading course resources…</p>
        ) : null}

        {resources.accessDenied ? (
          <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">
            Course resources are private to enrolled learners and course instructors. Enroll in a
            cohort to access files shared in this channel.
          </div>
        ) : null}

        {resources.error ? (
          <p className="mb-3 rounded-lg border border-rose-500/30 bg-rose-500/10 px-4 py-2 text-sm text-rose-200">
            {resources.error}
          </p>
        ) : null}

        {resources.uploadSuccess ? (
          <p className="mb-3 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-2 text-sm text-emerald-200">
            {resources.uploadSuccess}
          </p>
        ) : null}

        {!resources.isLoading && resources.canView ? (
          <>
            <h3 className="text-sm font-semibold text-app-text">Course resources</h3>
            {resources.filteredResources.length === 0 ? (
              <p className="mt-3 text-sm text-app-muted">
                {resources.resources.length === 0
                  ? 'No resources uploaded yet.'
                  : 'No resources match your search or filter.'}
              </p>
            ) : (
              <ul className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {resources.filteredResources.map((resource) => (
                  <li key={resource.id}>
                    <ResourceCard
                      resource={resource}
                      isDownloading={resources.downloadingResourceId === resource.id}
                      onDownload={() => void resources.downloadResource(resource)}
                      onPreview={() => void resources.previewResource(resource)}
                    />
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : null}
      </div>
    </section>
  )
}

export function ResourcesChannelGate({
  serverId,
  channelId,
}: {
  serverId: string | undefined
  channelId: string
}) {
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const channelContext = findCourseChannelContext(navigationQuery.data, channelId)

  if (navigationQuery.isLoading) {
    return (
      <ConversationFrame title="Loading #resources…">
        <p className="text-sm text-app-muted">Checking your access to this channel.</p>
      </ConversationFrame>
    )
  }

  if (navigationQuery.isError || !channelContext || !serverId) {
    return (
      <ConversationFrame title="#resources unavailable">
        <p className="text-sm text-app-muted">You do not have access to this channel.</p>
      </ConversationFrame>
    )
  }

  return <ResourcesChannelConversation key={channelId} channelContext={channelContext} />
}

function ResourceCard({
  resource,
  isDownloading,
  onDownload,
  onPreview,
}: {
  resource: CourseResource
  isDownloading: boolean
  onDownload: () => void
  onPreview: () => void
}) {
  const kind = resourceFileKind(resource)
  const kindLabel = resourceKindLabel(kind)
  const canPreview = isPdfResource(resource)

  return (
    <article className="flex h-full flex-col rounded-xl border border-app-border bg-app-surface p-4">
      <div className="flex items-start gap-3">
        <ResourceIcon kind={kind} />
        <div className="min-w-0 flex-1">
          <div className="flex items-start gap-2">
            <h4 className="text-sm font-semibold text-app-text">{resource.title}</h4>
            {resource.aiApproved ? (
              <span className="shrink-0 rounded bg-app-accent/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-app-accent">
                AI
              </span>
            ) : null}
          </div>
          <p className="mt-1 text-xs text-app-muted">
            {kindLabel} · {formatByteSize(resource.byteSize)}
          </p>
        </div>
      </div>

      <p className="mt-3 line-clamp-2 text-xs text-app-muted">{resource.fileName}</p>

      <div className="mt-4 flex gap-2">
        {canPreview ? (
          <button
            type="button"
            className="flex-1 rounded-md border border-app-border px-3 py-1.5 text-xs font-semibold text-app-muted hover:bg-app-bg disabled:opacity-60"
            onClick={onPreview}
            disabled={isDownloading}
          >
            Preview
          </button>
        ) : null}
        <button
          type="button"
          className={`rounded-md bg-app-accent px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-60 ${canPreview ? 'flex-1' : 'w-full'}`}
          onClick={onDownload}
          disabled={isDownloading}
        >
          {isDownloading ? 'Loading…' : 'Download'}
        </button>
      </div>
    </article>
  )
}

function ResourceIcon({ kind }: { kind: CourseResourceFilter }) {
  const colorClass =
    kind === 'pdf'
      ? 'text-rose-300'
      : kind === 'slides'
        ? 'text-orange-300'
        : kind === 'assignments'
          ? 'text-emerald-300'
          : 'text-app-muted'

  return (
    <span
      className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-app-border bg-app-bg text-xs font-bold uppercase ${colorClass}`}
      aria-hidden
    >
      {kind === 'slides' ? 'PPT' : kind === 'assignments' ? 'ASN' : kind === 'pdf' ? 'PDF' : 'DOC'}
    </span>
  )
}

function AiApprovedBanner({ approvedCount }: { approvedCount: number }) {
  return (
    <aside className="max-w-sm rounded-xl border border-app-border bg-app-surface p-3">
      <div className="flex items-center gap-2">
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-app-accent text-xs font-bold text-white">
          ✓
        </span>
        <h3 className="text-sm font-semibold text-app-text">Approved for AI</h3>
      </div>
      <p className="mt-2 text-xs text-app-muted">
        {approvedCount > 0
          ? `${approvedCount} resource${approvedCount === 1 ? '' : 's'} verified safe and AI-friendly for grounding.`
          : 'Mark uploads as AI-approved when they are safe for the Study Assistant to cite.'}
      </p>
    </aside>
  )
}

function ConversationFrame({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex min-w-0 flex-1 flex-col bg-app-bg">
      <div className="border-b border-app-border px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-app-accent">
          Course resources
        </p>
        <h2 className="mt-1 text-base font-semibold text-app-text">{title}</h2>
      </div>
      <div className="flex flex-1 items-center justify-center p-6">{children}</div>
    </section>
  )
}
