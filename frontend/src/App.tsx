import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

type HealthResponse = {
  status: string
  service?: string
}

type Channel = {
  id: string
  name: string
  kind: 'TEXT' | 'VOICE'
}

type StudyServer = {
  id: string
  name: string
  ownerRole: {
    userId: string
    role: string
  }
  channels: Channel[]
}

type Course = {
  id: string
  title: string
  instructorRole: {
    userId: string
    role: string
  }
  cohort: {
    id: string
    name: string
  }
  channels: Channel[]
}

type HealthState = {
  gateway: string
  auth: string
  community: string
}

const createUserId = () => {
  if ('crypto' in window && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID()
  }

  return '00000000-0000-4000-8000-000000000001'
}

function App() {
  const [health, setHealth] = useState<HealthState>({
    gateway: 'checking',
    auth: 'checking',
    community: 'checking',
  })
  const [ownerUserId] = useState(createUserId)
  const [instructorUserId] = useState(createUserId)
  const [learnerUserId] = useState(createUserId)
  const [nonEnrolledUserId] = useState(createUserId)
  const [serverName, setServerName] = useState('Java Spring Study Group')
  const [courseTitle, setCourseTitle] = useState('Spring Boot Foundations')
  const [cohortName, setCohortName] = useState('Summer 2026')
  const [studyServer, setStudyServer] = useState<StudyServer | null>(null)
  const [course, setCourse] = useState<Course | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [isCreatingCourse, setIsCreatingCourse] = useState(false)
  const [isEnrolling, setIsEnrolling] = useState(false)
  const [accessResult, setAccessResult] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/actuator/health')
      .then((response) => response.json())
      .then((data) => setHealth((current) => ({ ...current, gateway: data.status ?? 'unknown' })))
      .catch(() => setHealth((current) => ({ ...current, gateway: 'unreachable' })))

    fetch('/api/v1/auth/health')
      .then((response) => response.json())
      .then((data: HealthResponse) => setHealth((current) => ({ ...current, auth: data.status ?? 'unknown' })))
      .catch(() => setHealth((current) => ({ ...current, auth: 'unreachable' })))

    fetch('/api/v1/study-servers/00000000-0000-0000-0000-000000000000')
      .then((response) => {
        setHealth((current) => ({
          ...current,
          community: response.status === 404 ? 'ok' : response.ok ? 'ok' : 'unknown',
        }))
      })
      .catch(() => setHealth((current) => ({ ...current, community: 'unreachable' })))
  }, [])

  const textChannels = useMemo(
    () => studyServer?.channels.filter((channel) => channel.kind === 'TEXT') ?? [],
    [studyServer],
  )
  const voiceChannels = useMemo(
    () => studyServer?.channels.filter((channel) => channel.kind === 'VOICE') ?? [],
    [studyServer],
  )

  const createStudyServer = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsCreating(true)
    setError(null)

    try {
      const response = await fetch('/api/v1/study-servers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: serverName, ownerUserId }),
      })

      if (!response.ok) {
        throw new Error(`Create failed with ${response.status}`)
      }

      const created: StudyServer = await response.json()
      const viewedResponse = await fetch(`/api/v1/study-servers/${created.id}`)

      if (!viewedResponse.ok) {
        throw new Error(`View failed with ${viewedResponse.status}`)
      }

      setStudyServer(await viewedResponse.json())
      setCourse(null)
      setAccessResult(null)
      setHealth((current) => ({ ...current, community: 'ok' }))
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to create Study Server')
    } finally {
      setIsCreating(false)
    }
  }

  const createCourseAndCohort = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!studyServer) {
      return
    }

    setIsCreatingCourse(true)
    setError(null)
    setAccessResult(null)

    try {
      const response = await fetch(`/api/v1/study-servers/${studyServer.id}/courses`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ownerUserId,
          title: courseTitle,
          instructorUserId,
          cohortName,
        }),
      })

      if (!response.ok) {
        throw new Error(`Course create failed with ${response.status}`)
      }

      setCourse(await response.json())
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to create Course')
    } finally {
      setIsCreatingCourse(false)
    }
  }

  const enrollLearner = async () => {
    if (!course) {
      return
    }

    setIsEnrolling(true)
    setError(null)
    setAccessResult(null)

    try {
      const response = await fetch(`/api/v1/cohorts/${course.cohort.id}/enrollments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ instructorUserId, learnerUserId }),
      })

      if (!response.ok) {
        throw new Error(`Enroll failed with ${response.status}`)
      }

      const firstChannel = course.channels[0]
      const learnerAccess = await fetch(
        `/api/v1/course-channels/${firstChannel.id}?viewerUserId=${learnerUserId}`,
      )
      const outsiderAccess = await fetch(
        `/api/v1/course-channels/${firstChannel.id}?viewerUserId=${nonEnrolledUserId}`,
      )

      if (!learnerAccess.ok || outsiderAccess.status !== 403) {
        throw new Error('Enrollment access check failed')
      }

      setAccessResult('Learner can access Course Channels; non-enrolled user is blocked.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to enroll learner')
    } finally {
      setIsEnrolling(false)
    }
  }

  return (
    <main className="app-shell">
      <aside className="workspace-panel" aria-label="Study Server setup">
        <div>
          <p className="eyebrow">Chanter</p>
          <h1>Study Servers</h1>
        </div>

        <form className="create-form" onSubmit={createStudyServer}>
          <label htmlFor="server-name">Study Server name</label>
          <input
            id="server-name"
            name="server-name"
            value={serverName}
            onChange={(event) => setServerName(event.target.value)}
            maxLength={120}
            required
          />
          <button type="submit" disabled={isCreating}>
            {isCreating ? 'Creating...' : 'Create Study Server'}
          </button>
          {error ? <p className="form-error">{error}</p> : null}
        </form>

        <div className="owner-block">
          <span>Owner</span>
          <code>{ownerUserId}</code>
          <span>Instructor</span>
          <code>{instructorUserId}</code>
          <span>Learner</span>
          <code>{learnerUserId}</code>
        </div>
      </aside>

      <section className="server-surface" aria-live="polite">
        {studyServer ? (
          <>
            <header className="server-header">
              <div>
                <p className="eyebrow">Created Study Server</p>
                <h2>{studyServer.name}</h2>
              </div>
              <span className="role-pill">{studyServer.ownerRole.role.replaceAll('_', ' ')}</span>
            </header>

            <div className="channel-layout">
              <nav className="channel-list" aria-label="Default channels">
                <section>
                  <h3>Text Channels</h3>
                  {textChannels.map((channel) => (
                    <a href={`#${channel.id}`} key={channel.id}>
                      <span>#</span>
                      {channel.name}
                    </a>
                  ))}
                </section>
                <section>
                  <h3>Voice Channels</h3>
                  {voiceChannels.map((channel) => (
                    <a href={`#${channel.id}`} key={channel.id}>
                      <span>&gt;</span>
                      {channel.name}
                    </a>
                  ))}
                </section>
                {course ? (
                  <section>
                    <h3>Course Channels</h3>
                    {course.channels.map((channel) => (
                      <a href={`#${channel.id}`} key={channel.id}>
                        <span>#</span>
                        {channel.name}
                      </a>
                    ))}
                  </section>
                ) : null}
              </nav>

              <article className="conversation-pane">
                <p className="system-line">Welcome to {studyServer.name}</p>
                <p>
                  The Study Server shell is ready with default Study Server Channels and the creator
                  assigned as Owner.
                </p>
                <form className="course-form" onSubmit={createCourseAndCohort}>
                  <label htmlFor="course-title">Course title</label>
                  <input
                    id="course-title"
                    value={courseTitle}
                    onChange={(event) => setCourseTitle(event.target.value)}
                    maxLength={160}
                    required
                  />
                  <label htmlFor="cohort-name">Cohort name</label>
                  <input
                    id="cohort-name"
                    value={cohortName}
                    onChange={(event) => setCohortName(event.target.value)}
                    maxLength={120}
                    required
                  />
                  <button type="submit" disabled={isCreatingCourse}>
                    {isCreatingCourse ? 'Creating...' : 'Create Course + Cohort'}
                  </button>
                </form>
                {course ? (
                  <section className="course-summary">
                    <div>
                      <p className="eyebrow">Created Course</p>
                      <h3>{course.title}</h3>
                      <p>{course.cohort.name}</p>
                    </div>
                    <button type="button" onClick={enrollLearner} disabled={isEnrolling}>
                      {isEnrolling ? 'Enrolling...' : 'Enroll Learner'}
                    </button>
                    {accessResult ? <p className="system-line">{accessResult}</p> : null}
                  </section>
                ) : null}
              </article>
            </div>
          </>
        ) : (
          <div className="empty-state">
            <p className="eyebrow">Issue #12</p>
            <h2>Create a Study Server</h2>
            <p>Use the setup panel to create the first learning community shell.</p>
          </div>
        )}
      </section>

      <aside className="status-panel" aria-label="Service status">
        <h2>Services</h2>
        <StatusRow label="Gateway" value={health.gateway} />
        <StatusRow label="Auth" value={health.auth} />
        <StatusRow label="Community" value={health.community} />
      </aside>
    </main>
  )
}

function StatusRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="status-row">
      <span>{label}</span>
      <strong className={`status ${value.toLowerCase()}`}>{value}</strong>
    </div>
  )
}

export default App
