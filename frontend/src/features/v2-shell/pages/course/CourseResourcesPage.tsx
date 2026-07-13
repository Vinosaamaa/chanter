import { useMemo, useRef, useState, type ChangeEvent } from 'react'
import { Check, ChevronDown, ClipboardList, Download, ExternalLink, FileText, Folder, Link2, Monitor, Play, Search, Sparkles, X } from 'lucide-react'

import { useAuthStore } from '../../../../stores/auth-store'
import { formatByteSize, resourceFileKind, resourceKindLabel } from '../../../resources/course-resource-format'
import type { CourseResource, CourseResourceFilter } from '../../../resources/course-resource-types'
import { useCourseResourcesChannel } from '../../../shell/hooks/use-course-resources-channel'
import { useStudyAssistantInstallFlow } from '../../../study-assistant/hooks/use-study-assistant-install'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

type DemoResource = {
  id: string
  title: string
  detail: string
  kind: 'pdf' | 'recording' | 'assignment' | 'slides' | 'link'
  aiApproved?: boolean
  isNew?: boolean
}

const weeks: { title: string; resources: DemoResource[] }[] = [
  { title: 'Week 1 — Foundations', resources: [
    { id: 'lecture-1', title: 'Lecture 1 — Intro & Course Logistics', detail: 'PDF · 2.4 MB · Dr. Johnson', kind: 'pdf', aiApproved: true },
    { id: 'recording-1', title: 'Lecture 1 Recording', detail: 'Video · 48 min', kind: 'recording', aiApproved: true },
    { id: 'problem-set-1', title: 'Problem Set 1', detail: 'Due Sunday 11:59 PM', kind: 'assignment' },
  ] },
  { title: 'Week 2 — Recursion & Complexity', resources: [
    { id: 'lecture-2', title: 'Lecture 2 — Recursion Deep Dive', detail: 'Slides · 34 pages', kind: 'slides', aiApproved: true, isNew: true },
    { id: 'visualizer', title: 'Merge Sort Visualizer', detail: 'External link · visualgo.net', kind: 'link' },
  ] },
]

const filters: { id: CourseResourceFilter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'slides', label: 'Slides' },
  { id: 'other', label: 'Recordings' },
  { id: 'assignments', label: 'Assignments' },
]

export function CourseResourcesPage() {
  const { course, serverId, isOwner } = useV2CourseWorkspace()
  const userId = useAuthStore((state) => state.user?.id)
  const resources = useCourseResourcesChannel(course.id)
  const install = useStudyAssistantInstallFlow({ studyServerId: serverId, instructorUserId: userId })
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [installRequested, setInstallRequested] = useState(false)

  const visibleWeeks = useMemo(() => {
    const query = resources.searchQuery.trim().toLowerCase()
    return weeks.map((week) => ({
      ...week,
      resources: week.resources.filter((resource) => {
        const matchesSearch = !query || resource.title.toLowerCase().includes(query) || resource.detail.toLowerCase().includes(query)
        const matchesFilter = resources.activeFilter === 'all'
          || (resources.activeFilter === 'slides' && resource.kind === 'slides')
          || (resources.activeFilter === 'assignments' && resource.kind === 'assignment')
          || (resources.activeFilter === 'other' && resource.kind === 'recording')
          || (resources.activeFilter === 'pdf' && resource.kind === 'pdf')
        return matchesSearch && matchesFilter
      }),
    })).filter((week) => week.resources.length)
  }, [resources.activeFilter, resources.searchQuery])

  const chooseFile = () => fileInputRef.current?.click()
  const onFileSelected = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (file) void resources.uploadResource(file, { title: file.name, aiApproved: true })
  }
  const openInstall = () => {
    setInstallRequested(true)
    void install.openInstallDialog()
  }
  const closeInstall = () => {
    setInstallRequested(false)
    install.closeDialog()
  }

  return (
    <div className="resources-page">
      <div className="resources-toolbar">
        <label><Search /><input value={resources.searchQuery} onChange={(event) => resources.setSearchQuery(event.target.value)} placeholder="Search resources…" /></label>
        <div className="resource-filters">{filters.map((filter) => <button type="button" key={filter.id} className={resources.activeFilter === filter.id ? 'active' : undefined} onClick={() => resources.setActiveFilter(filter.id)}>{filter.label}</button>)}</div>
        {isOwner || resources.canUpload ? <div className="resource-owner-actions"><button type="button" className="v2-primary-button" onClick={chooseFile} disabled={resources.isUploading}>{resources.isUploading ? 'Uploading…' : 'Upload'}</button><input ref={fileInputRef} type="file" hidden onChange={onFileSelected} /><button type="button" className="v2-outline-button" onClick={openInstall}><Sparkles />Install AI Study Assistant</button></div> : null}
      </div>

      {resources.error && course.id !== 'course-demo' ? <p className="inline-error">{resources.error}</p> : null}
      {resources.uploadSuccess ? <p className="inline-success">{resources.uploadSuccess}</p> : null}

      <div className="resource-week-list">
        {visibleWeeks.map((week) => (
          <section className="resource-week" key={week.title}>
            <header><Folder /><h2>{week.title}</h2><ChevronDown /></header>
            <div className="resource-rows">
              {week.resources.map((resource) => <DemoResourceRow resource={resource} key={resource.id} />)}
            </div>
          </section>
        ))}
        {resources.filteredResources.length > 0 ? (
          <section className="resource-week">
            <header><Folder /><h2>Course uploads</h2><ChevronDown /></header>
            <div className="resource-rows">{resources.filteredResources.map((resource) => <LiveResourceRow key={resource.id} resource={resource} onOpen={() => void resources.downloadResource(resource)} />)}</div>
          </section>
        ) : null}
        {visibleWeeks.length === 0 && resources.filteredResources.length === 0 ? <div className="resource-empty">No resources match your search.</div> : null}
      </div>

      {installRequested ? <InstallAssistantModal install={install} onClose={closeInstall} /> : null}
    </div>
  )
}

function DemoResourceRow({ resource }: { resource: DemoResource }) {
  const icons = { pdf: <span className="pdf-label">PDF</span>, recording: <Play />, assignment: <ClipboardList />, slides: <Monitor />, link: <Link2 /> }
  return (
    <article className="resource-row">
      <span className={`resource-type-icon ${resource.kind}`}>{icons[resource.kind]}</span>
      <div><strong>{resource.title}</strong><p>{resource.detail}</p></div>
      <span className="resource-statuses">{resource.aiApproved ? <b><Check />AI-approved</b> : null}{resource.kind === 'assignment' ? <b className="due">Due soon</b> : null}{resource.isNew ? <i>● New</i> : null}</span>
      <button type="button" aria-label={resource.kind === 'link' ? 'Open resource' : 'Download resource'}>{resource.kind === 'link' ? <ExternalLink /> : <Download />}</button>
    </article>
  )
}

function LiveResourceRow({ resource, onOpen }: { resource: CourseResource; onOpen: () => void }) {
  const kind = resourceFileKind(resource)
  return (
    <article className="resource-row">
      <span className={`resource-type-icon ${kind}`}><FileText /></span>
      <div><strong>{resource.title}</strong><p>{resourceKindLabel(kind)} · {formatByteSize(resource.byteSize)}</p></div>
      <span className="resource-statuses">{resource.aiApproved ? <b><Check />AI-approved</b> : null}</span>
      <button type="button" onClick={onOpen} aria-label={`Download ${resource.title}`}><Download /></button>
    </article>
  )
}

type InstallFlow = ReturnType<typeof useStudyAssistantInstallFlow>

function InstallAssistantModal({ install, onClose }: { install: InstallFlow; onClose: () => void }) {
  const [permissions, setPermissions] = useState([true, true, true, true, true])
  const labels = ['Read and analyze course resources', 'Organize and tag content', 'Generate titles and summaries', 'Answer questions about course content', 'Create study guides and practice questions']
  return (
    <div className="v2-modal-backdrop" role="presentation">
      <section className="assistant-install-modal" role="dialog" aria-modal="true" aria-labelledby="assistant-install-title">
        <button type="button" className="modal-close" onClick={onClose} aria-label="Close"><X /></button>
        <h2 id="assistant-install-title">Install AI Study Assistant</h2>
        <p>AI Study Assistant can help you save time by automatically tagging, organizing, and summarizing your resources.</p>
        <h3>Allow AI Study Assistant to:</h3>
        <div className="assistant-permissions">{labels.map((label,index) => <label key={label}><input type="checkbox" checked={permissions[index]} onChange={(event) => setPermissions((current) => current.map((value,itemIndex) => itemIndex === index ? event.target.checked : value))} /><span>{label}</span></label>)}</div>
        {install.installError ? <p className="modal-error">{install.installError}</p> : null}
        <footer><button type="button" className="v2-outline-button" onClick={onClose}>Cancel</button><button type="button" className="v2-primary-button" onClick={install.confirmInstall} disabled={!install.preview || install.isInstalling || !permissions.some(Boolean)}>{install.isOpening ? 'Loading…' : install.isInstalling ? 'Installing…' : 'Install'}</button></footer>
      </section>
    </div>
  )
}
