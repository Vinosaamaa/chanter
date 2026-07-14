import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, Sprout, UsersRound, X } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

import {
  courseCatalogQueryKey,
  fetchCourseCatalog,
  joinDiscoveredCohort,
  type CourseDiscoveryFilter,
  type DiscoveredCohort,
  type DiscoveredCourse,
} from '../../../course-discovery/course-discovery-api'
import { fetchPublicProfiles } from '../../../friends/friends-api'
import { createCourse } from '../../../onboarding/onboarding-api'
import { formatUserFacingApiError } from '../../../../lib/format-api-error'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2Community } from '../../layouts/v2-community-context'
import { v2CoursePath } from '../../v2-routes'

const filters: Array<{ label: string; value: CourseDiscoveryFilter }> = [
  { label: 'All active', value: 'ALL' },
  { label: 'Enrolled', value: 'ENROLLED' },
  { label: 'Open', value: 'OPEN' },
  { label: 'Opening soon', value: 'OPENING_SOON' },
]

const courseColors = ['#5270ff', '#45b563', '#875ce8', '#23b4db']

type InviteTarget = {
  course: DiscoveredCourse
  cohort: DiscoveredCohort
}

export function CommunityDiscoverPage() {
  const { studyServerCapabilities, serverId } = useV2Community()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<CourseDiscoveryFilter>('ALL')
  const [selectedCohorts, setSelectedCohorts] = useState<Record<string, string>>({})
  const [joiningCohortId, setJoiningCohortId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [inviteTarget, setInviteTarget] = useState<InviteTarget | null>(null)
  const [showCreate, setShowCreate] = useState(false)

  const catalogQuery = useQuery({
    queryKey: courseCatalogQueryKey(serverId, query, filter),
    queryFn: () => fetchCourseCatalog(serverId, { search: query, filter }),
    enabled: Boolean(serverId),
  })
  const instructorIds = useMemo(
    () => [...new Set((catalogQuery.data?.courses ?? []).map((course) => course.instructorUserId))].sort(),
    [catalogQuery.data?.courses],
  )
  const profilesQuery = useQuery({
    queryKey: ['course-catalog-profiles', ...instructorIds],
    queryFn: () => fetchPublicProfiles(instructorIds),
    enabled: instructorIds.length > 0,
  })
  const profilesById = useMemo(
    () => new Map((profilesQuery.data?.profiles ?? []).map((profile) => [profile.userId, profile])),
    [profilesQuery.data?.profiles],
  )

  const join = async (course: DiscoveredCourse, cohort: DiscoveredCohort, inviteCode?: string) => {
    setJoiningCohortId(cohort.id)
    setActionError(null)
    try {
      if (inviteCode) {
        await joinDiscoveredCohort(cohort.id, inviteCode)
      } else {
        await joinDiscoveredCohort(cohort.id)
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['course-catalog', serverId] }),
        queryClient.invalidateQueries({ queryKey: ['study-server-navigation'] }),
        queryClient.invalidateQueries({ queryKey: ['study-servers'] }),
      ])
      setInviteTarget(null)
      navigate(`${v2CoursePath(serverId, course.id)}?cohort=${encodeURIComponent(cohort.id)}`)
    } catch (caught) {
      setActionError(formatUserFacingApiError(caught, 'Unable to join this Cohort.'))
    } finally {
      setJoiningCohortId(null)
    }
  }

  return (
    <div className="community-discover-page">
      <header>
        <div>
          <h2>Courses in this hub</h2>
          <p>Enroll in a Cohort or enter an invite code</p>
        </div>
        {studyServerCapabilities?.canCreateCourse ? (
          <button type="button" className="v2-primary-button" onClick={() => setShowCreate(true)}>
            <Plus />Create course
          </button>
        ) : null}
      </header>

      <label className="discover-search">
        <Search />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search courses in this hub..."
          aria-label="Search courses in this hub"
        />
      </label>
      <div className="discover-filters" aria-label="Course filters">
        {filters.map((item) => (
          <button
            type="button"
            className={filter === item.value ? 'active' : undefined}
            aria-pressed={filter === item.value}
            onClick={() => setFilter(item.value)}
            key={item.value}
          >
            {item.label}
          </button>
        ))}
      </div>

      {actionError ? <p className="inline-error" role="alert">{actionError}</p> : null}
      {catalogQuery.isLoading ? <div className="discover-status" role="status">Loading Courses...</div> : null}
      {catalogQuery.isError ? (
        <div className="discover-status" role="alert">
          Unable to load Courses. <button type="button" onClick={() => void catalogQuery.refetch()}>Try again</button>
        </div>
      ) : null}
      {catalogQuery.isSuccess && catalogQuery.data.courses.length === 0 ? (
        <div className="discover-status">
          <Search />
          <strong>No Courses found</strong>
          <span>Try another search or filter.</span>
        </div>
      ) : null}

      <div className="discover-course-grid">
        {(catalogQuery.data?.courses ?? []).map((course, index) => {
          const selected = selectCohort(course, selectedCohorts[course.id])
          const instructorName = profilesById.get(course.instructorUserId)?.displayName
            ?? (profilesQuery.isLoading ? 'Loading instructor...' : 'Instructor unavailable')
          return (
            <article key={course.id}>
              <h3><i style={{ background: courseColors[index % courseColors.length] }} />{course.title}</h3>
              {course.cohorts.length > 1 ? (
                <label className="cohort-chip cohort-picker">
                  <Sprout />
                  <select
                    aria-label={`Cohort for ${course.title}`}
                    value={selected?.id ?? ''}
                    onChange={(event) => setSelectedCohorts((current) => ({
                      ...current,
                      [course.id]: event.target.value,
                    }))}
                  >
                    {course.cohorts.map((cohort) => <option value={cohort.id} key={cohort.id}>{cohort.name}</option>)}
                  </select>
                </label>
              ) : selected ? (
                <b className="cohort-chip"><Sprout />{selected.name}</b>
              ) : null}
              <p><V2Avatar name={instructorName} tone="amber" size="sm" />{instructorName}</p>
              <p><UsersRound />{selected?.learnerCount ?? 0} learners</p>
              <footer>
                {selected ? (
                  <CourseAction
                    course={course}
                    cohort={selected}
                    serverId={serverId}
                    joining={joiningCohortId === selected.id}
                    onJoin={() => void join(course, selected)}
                    onInvite={() => {
                      setActionError(null)
                      setInviteTarget({ course, cohort: selected })
                    }}
                  />
                ) : <b className="unavailable">Unavailable</b>}
              </footer>
            </article>
          )
        })}
      </div>

      {inviteTarget ? (
        <InviteCodeModal
          target={inviteTarget}
          busy={joiningCohortId === inviteTarget.cohort.id}
          error={actionError}
          onClose={() => { setInviteTarget(null); setActionError(null) }}
          onSubmit={(inviteCode) => void join(inviteTarget.course, inviteTarget.cohort, inviteCode)}
        />
      ) : null}
      {showCreate ? (
        <CreateCourseModal
          serverId={serverId}
          onClose={() => setShowCreate(false)}
          onCreated={async () => {
            await Promise.all([
              queryClient.invalidateQueries({ queryKey: ['course-catalog', serverId] }),
              queryClient.invalidateQueries({ queryKey: ['study-server-navigation'] }),
            ])
            setShowCreate(false)
          }}
        />
      ) : null}
    </div>
  )
}

function selectCohort(course: DiscoveredCourse, selectedId: string | undefined) {
  return course.cohorts.find((cohort) => cohort.id === selectedId)
    ?? course.cohorts.find((cohort) => cohort.enrolled)
    ?? course.cohorts[0]
}

function CourseAction({
  course,
  cohort,
  serverId,
  joining,
  onJoin,
  onInvite,
}: {
  course: DiscoveredCourse
  cohort: DiscoveredCohort
  serverId: string
  joining: boolean
  onJoin: () => void
  onInvite: () => void
}) {
  if (cohort.enrolled) {
    return (
      <>
        <b>Enrolled</b>
        <Link to={`${v2CoursePath(serverId, course.id)}?cohort=${encodeURIComponent(cohort.id)}`}>
          Open course
        </Link>
      </>
    )
  }
  if (cohort.enrollmentPolicy === 'OPEN') {
    return (
      <button
        type="button"
        className="v2-primary-button"
        disabled={joining}
        aria-label={`Join ${course.title}`}
        onClick={onJoin}
      >
        {joining ? 'Joining...' : 'Join'}
      </button>
    )
  }
  if (cohort.enrollmentPolicy === 'INVITE_ONLY') {
    return <button type="button" onClick={onInvite}>Enter invite code</button>
  }
  return (
    <b className="unavailable">
      {cohort.enrollmentPolicy === 'OPENING_SOON' ? 'Opening soon' : 'Unavailable'}
    </b>
  )
}

function InviteCodeModal({
  target,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  target: InviteTarget
  busy: boolean
  error: string | null
  onClose: () => void
  onSubmit: (inviteCode: string) => void
}) {
  const [inviteCode, setInviteCode] = useState('')
  const dialogRef = useModalFocus(onClose, busy)
  return (
    <div className="v2-modal-backdrop" role="presentation">
      <form
        ref={dialogRef}
        className="apply-instructor-modal invite-code-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="invite-code-title"
        onSubmit={(event) => { event.preventDefault(); onSubmit(inviteCode.trim()) }}
      >
        <button type="button" className="modal-close" aria-label="Close invite code" disabled={busy} onClick={onClose}><X /></button>
        <h2 id="invite-code-title">Join {target.course.title}</h2>
        <p>{target.cohort.name} requires an invite code.</p>
        <label>
          Invite code
          <input
            data-initial-focus
            required
            value={inviteCode}
            onChange={(event) => setInviteCode(event.target.value)}
          />
        </label>
        {error ? <p className="modal-error" role="alert">{error}</p> : null}
        <footer>
          <button type="button" className="v2-outline-button" disabled={busy} onClick={onClose}>Cancel</button>
          <button type="submit" className="v2-primary-button" disabled={busy || !inviteCode.trim()}>
            {busy ? 'Joining...' : 'Join Cohort'}
          </button>
        </footer>
      </form>
    </div>
  )
}

function CreateCourseModal({
  serverId,
  onClose,
  onCreated,
}: {
  serverId: string
  onClose: () => void
  onCreated: () => Promise<void>
}) {
  const [code, setCode] = useState('PHYS 101')
  const [name, setName] = useState('Physics I')
  const [cohort, setCohort] = useState('Fall 2026')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const dialogRef = useModalFocus(onClose, busy)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await createCourse(serverId, { title: `${code} - ${name}`, cohortName: cohort })
      await onCreated()
    } catch (caught) {
      setError(formatUserFacingApiError(caught, 'Unable to create Course.'))
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="v2-modal-backdrop" role="presentation">
      <form
        ref={dialogRef}
        className="create-course-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-course-title"
        onSubmit={(event) => void submit(event)}
      >
        <button type="button" className="modal-close" aria-label="Close create Course" disabled={busy} onClick={onClose}><X /></button>
        <h2 id="create-course-title">Create course</h2>
        <label>Course code<input data-initial-focus value={code} onChange={(event) => setCode(event.target.value)} required /></label>
        <label>Course name<input value={name} onChange={(event) => setName(event.target.value)} required /></label>
        <label>
          First Cohort
          <select value={cohort} onChange={(event) => setCohort(event.target.value)}>
            <option>Fall 2026</option>
            <option>Spring 2027</option>
          </select>
        </label>
        {error ? <p className="modal-error" role="alert">{error}</p> : null}
        <footer>
          <button type="button" className="v2-outline-button" disabled={busy} onClick={onClose}>Cancel</button>
          <button type="submit" className="v2-primary-button" disabled={busy}>{busy ? 'Creating...' : 'Create course'}</button>
        </footer>
      </form>
    </div>
  )
}

function useModalFocus(onClose: () => void, busy: boolean) {
  const dialogRef = useRef<HTMLFormElement>(null)
  const closeRef = useRef(onClose)
  const busyRef = useRef(busy)

  useEffect(() => {
    closeRef.current = onClose
    busyRef.current = busy
  }, [busy, onClose])

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null
    const focusableSelector = [
      'button:not([disabled])',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[href]',
      '[tabindex]:not([tabindex="-1"])',
    ].join(',')
    const dialog = dialogRef.current
    const initialFocus = dialog?.querySelector<HTMLElement>('[data-initial-focus]')
      ?? dialog?.querySelector<HTMLElement>(focusableSelector)
    initialFocus?.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (!busyRef.current) {
          event.preventDefault()
          closeRef.current()
        }
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return

      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(focusableSelector),
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
      previousFocus?.focus()
    }
  }, [])

  return dialogRef
}
