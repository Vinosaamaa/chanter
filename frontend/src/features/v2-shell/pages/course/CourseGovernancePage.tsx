import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Archive, ArrowLeft } from 'lucide-react'

import {
  addCourseCohort,
  archiveCourse,
  assignCourseInstructor,
  fetchCourseLifecycle,
  publishCourse,
  unpublishCourse,
  updateCourseMetadata,
} from '../../../course-lifecycle/course-lifecycle-api'
import { formatUserFacingApiError } from '../../../../lib/format-api-error'
import { useStudyServerNavigationQuery } from '../../../shell/hooks/use-shell-queries'
import { v2CommunityPath } from '../../v2-routes'

export function CourseGovernancePage() {
  const { serverId, courseId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const navigation = useStudyServerNavigationQuery(serverId)
  const [titleEdits, setTitleEdits] = useState<string | undefined>()
  const [descriptionEdits, setDescriptionEdits] = useState<string | undefined>()
  const [cohortName, setCohortName] = useState('')
  const [instructorEmail, setInstructorEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [confirmArchive, setConfirmArchive] = useState(false)

  const lifecycleQuery = useQuery({
    queryKey: ['course-lifecycle', courseId],
    queryFn: () => fetchCourseLifecycle(courseId!),
    enabled: Boolean(courseId),
  })

  if (!serverId || !courseId) {
    return null
  }

  const isOwner = navigation.data?.capabilities.owner ?? false
  const lifecycle = lifecycleQuery.data

  if (navigation.isLoading || lifecycleQuery.isLoading) {
    return <section className="v2-workspace-page course-governance-page" role="status">Loading course settings…</section>
  }

  if (!isOwner) {
    return (
      <section className="v2-workspace-page course-governance-page">
        <h1>Course settings unavailable</h1>
        <p>Only the Study Server owner can manage this Course.</p>
        <Link className="v2-outline-button" to={v2CommunityPath(serverId, 'discover')}>Back to Courses</Link>
      </section>
    )
  }

  if (lifecycleQuery.isError || !lifecycle) {
    return (
      <section className="v2-workspace-page course-governance-page">
        <h1>Unable to load course settings</h1>
        <p role="alert">{formatUserFacingApiError(lifecycleQuery.error, 'This course could not be loaded.')}</p>
        <button type="button" className="v2-outline-button" onClick={() => void lifecycleQuery.refetch()}>Try again</button>
      </section>
    )
  }

  const invalidate = async () => {
    setTitleEdits(undefined)
    setDescriptionEdits(undefined)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['course-lifecycle', courseId] }),
      queryClient.invalidateQueries({ queryKey: ['study-server-navigation', serverId] }),
      queryClient.invalidateQueries({ queryKey: ['course-catalog', serverId] }),
    ])
  }

  const saveMetadata = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const title = (titleEdits ?? lifecycle.title).trim()
    const description = descriptionEdits ?? lifecycle.description ?? ''
    try {
      await updateCourseMetadata(courseId, { title, description })
      setTitleEdits(undefined)
      setDescriptionEdits(undefined)
      await invalidate()
    } catch (caught) {
      setError(formatUserFacingApiError(caught, 'Unable to save course details.'))
    }
  }

  const addCohort = async (event: FormEvent) => {
    event.preventDefault()
    if (!cohortName.trim()) return
    setError(null)
    try {
      await addCourseCohort(courseId, cohortName.trim())
      setCohortName('')
      await invalidate()
    } catch (caught) {
      setError(formatUserFacingApiError(caught, 'Unable to add Cohort.'))
    }
  }

  const assignInstructor = async (event: FormEvent) => {
    event.preventDefault()
    if (!instructorEmail.trim()) return
    setError(null)
    try {
      await assignCourseInstructor(courseId, { instructorEmail: instructorEmail.trim() })
      setInstructorEmail('')
      await invalidate()
    } catch (caught) {
      setError(formatUserFacingApiError(caught, 'Unable to assign instructor.'))
    }
  }

  const togglePublish = async () => {
    setError(null)
    try {
      if (lifecycle.published) {
        await unpublishCourse(courseId)
      } else {
        await publishCourse(courseId)
      }
      await invalidate()
    } catch (caught) {
      setError(formatUserFacingApiError(caught, 'Unable to update publish state.'))
    }
  }

  const confirmArchiveCourse = async () => {
    setError(null)
    try {
      await archiveCourse(courseId)
      await invalidate()
      navigate(v2CommunityPath(serverId, 'discover'), { replace: true })
    } catch (caught) {
      setError(formatUserFacingApiError(caught, 'Unable to archive course.'))
    }
  }

  return (
    <section className="v2-workspace-page course-governance-page">
      <header className="course-governance-header">
        <button type="button" className="v2-outline-button" onClick={() => navigate(-1)}>
          <ArrowLeft size={18} /> Back
        </button>
        <div>
          <h1>Course settings</h1>
          <p>
            {lifecycle.published ? 'Published' : 'Draft'}
            {lifecycle.archived ? ' · Archived' : ''}
          </p>
        </div>
      </header>

      {error ? <p className="inline-error" role="alert">{error}</p> : null}

      <form className="course-governance-card" onSubmit={(event) => void saveMetadata(event)}>
        <h2>Course details</h2>
        <label>
          Title
          <input
            value={titleEdits ?? lifecycle.title}
            onChange={(event) => setTitleEdits(event.target.value)}
            required
          />
        </label>
        <label>
          Description
          <textarea
            value={descriptionEdits ?? lifecycle.description ?? ''}
            onChange={(event) => setDescriptionEdits(event.target.value)}
            rows={4}
          />
        </label>
        <button type="submit" className="v2-primary-button">Save details</button>
      </form>

      {!lifecycle.cohort ? (
        <form className="course-governance-card" onSubmit={(event) => void addCohort(event)}>
          <h2>Add first Cohort</h2>
          <p>Create the Cohort before publishing this Course.</p>
          <label>
            Cohort name
            <input value={cohortName} onChange={(event) => setCohortName(event.target.value)} required />
          </label>
          <button type="submit" className="v2-primary-button">Add Cohort</button>
        </form>
      ) : (
        <section className="course-governance-card">
          <h2>Cohort</h2>
          <p>{lifecycle.cohort.name}</p>
        </section>
      )}

      <form className="course-governance-card" onSubmit={(event) => void assignInstructor(event)}>
        <h2>Instructor</h2>
        <p>
          {lifecycle.instructorRole
            ? `Assigned instructor user ${lifecycle.instructorRole.userId}`
            : 'Assign a course-scoped instructor by registered email.'}
        </p>
        <label>
          Instructor email
          <input
            type="email"
            value={instructorEmail}
            onChange={(event) => setInstructorEmail(event.target.value)}
            placeholder="instructor@school.edu"
          />
        </label>
        <button type="submit" className="v2-outline-button">Assign instructor</button>
      </form>

      <section className="course-governance-card">
        <h2>Visibility</h2>
        <p>
          {lifecycle.published
            ? 'Learners can discover this Course in the hub catalog.'
            : 'Draft courses stay hidden from the catalog until published.'}
        </p>
        <button
          type="button"
          className="v2-primary-button"
          disabled={!lifecycle.cohort || lifecycle.archived}
          onClick={() => void togglePublish()}
        >
          {lifecycle.published ? 'Unpublish course' : 'Publish course'}
        </button>
      </section>

      <section className="course-governance-card danger">
        <h2><Archive size={18} /> Archive course</h2>
        <p>Archiving removes this Course from discovery and unpublishes it.</p>
        {!confirmArchive ? (
          <button type="button" className="v2-outline-button" onClick={() => setConfirmArchive(true)}>
            Archive course…
          </button>
        ) : (
          <div className="confirm-row">
            <button type="button" className="v2-outline-button" onClick={() => setConfirmArchive(false)}>Cancel</button>
            <button type="button" className="v2-primary-button danger" onClick={() => void confirmArchiveCourse()}>
              Confirm archive
            </button>
          </div>
        )}
      </section>
    </section>
  )
}
