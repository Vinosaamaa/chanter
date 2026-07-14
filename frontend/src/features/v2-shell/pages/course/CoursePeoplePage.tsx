import { useState, type FormEvent } from 'react'
import {
  ChevronLeft,
  ChevronRight,
  Ellipsis,
  Link2,
  MessageCircle,
  Plus,
  Search,
  Trash2,
  UserMinus,
  X,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { formatUserFacingApiError } from '../../../../lib/format-api-error'
import { useAuthStore } from '../../../../stores/auth-store'
import { useCohortEnrollment } from '../../../onboarding/hooks/use-cohort-enrollment'
import { useCohortInvite } from '../../../onboarding/hooks/use-cohort-enrollments'
import type { CohortRosterMember } from '../../../people/cohort-roster-types'
import { useAcceptedFriendIds } from '../../../people/use-accepted-friend-ids'
import { useCohortRoster } from '../../../people/use-cohort-roster'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

type ManagerFilter = 'all' | 'enrolled' | 'pending'
type LearnerFilter = 'all' | 'staff' | 'classmates'
type AvatarTone = 'blue' | 'amber' | 'green' | 'purple' | 'rose'

const PAGE_SIZE = 8

export function CoursePeoplePage() {
  const { courseCapabilities, selectedCohort } = useV2CourseWorkspace()
  const canManagePeople = courseCapabilities.canManagePeople
  const cohortId = selectedCohort?.id
  const currentUserId = useAuthStore((state) => state.user?.id ?? null)
  const navigate = useNavigate()
  const rosterState = useCohortRoster(cohortId)
  const roster = rosterState.query.data
  const enrollment = useCohortEnrollment(cohortId ?? '')
  const inviteQuery = useCohortInvite(canManagePeople ? cohortId : undefined)
  const { friendIds, isLoading: areFriendsLoading } = useAcceptedFriendIds()
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<ManagerFilter | LearnerFilter>('all')
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [page, setPage] = useState(0)
  const [showAddTa, setShowAddTa] = useState(false)
  const [showEnroll, setShowEnroll] = useState(false)
  const [openMenuUserId, setOpenMenuUserId] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const normalizedQuery = query.trim().toLowerCase()
  const matchesQuery = (person: CohortRosterMember) =>
    !normalizedQuery ||
    person.displayName.toLowerCase().includes(normalizedQuery) ||
    Boolean(person.email?.toLowerCase().includes(normalizedQuery))
  const visibleLearners = (roster?.learners ?? []).filter((person) => {
    if (!matchesQuery(person)) return false
    if (!canManagePeople || filter === 'all') return true
    return person.status.toLowerCase() === filter
  })
  const rosterUserIds = new Set((roster?.learners ?? []).map((person) => person.userId))
  const selected = selectedIds.filter((userId) => rosterUserIds.has(userId))
  const pageCount = Math.max(1, Math.ceil(visibleLearners.length / PAGE_SIZE))
  const visiblePage = Math.min(page, pageCount - 1)
  const pagedLearners = visibleLearners.slice(
    visiblePage * PAGE_SIZE,
    (visiblePage + 1) * PAGE_SIZE,
  )
  const taCandidates = (roster?.learners ?? []).filter(
    (person) => person.status === 'ENROLLED' && matchesQuery(person),
  )

  const copyInvite = async () => {
    if (!cohortId || !inviteQuery.data) return
    const params = new URLSearchParams({ cohort: cohortId, invite: inviteQuery.data.inviteCode })
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/sign-in?${params.toString()}`)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch (caught) {
      setActionError(formatUserFacingApiError(caught, 'Unable to copy invite link.'))
    }
  }

  const assignTa = async (learnerUserIds: string[], teachingAssistantUserId: string | null) => {
    setActionError(null)
    try {
      await rosterState.assignTa.mutateAsync({ learnerUserIds, teachingAssistantUserId })
      setSelectedIds([])
    } catch (caught) {
      setActionError(formatUserFacingApiError(caught, 'Unable to assign teaching assistant.'))
    }
  }

  const removePerson = async (person: CohortRosterMember) => {
    const action = person.status === 'PENDING' ? 'cancel this invitation' : 'remove this learner'
    if (!window.confirm(`Are you sure you want to ${action} for ${person.displayName}?`)) return
    setActionError(null)
    try {
      if (person.status === 'PENDING' && person.invitationId) {
        await rosterState.cancelInvitation.mutateAsync(person.invitationId)
      } else {
        await rosterState.removeEnrollment.mutateAsync(person.userId)
      }
      setOpenMenuUserId(null)
    } catch (caught) {
      setActionError(formatUserFacingApiError(caught, 'Unable to update this enrollment.'))
    }
  }

  const removeTeachingAssistant = async (person: CohortRosterMember) => {
    if (!window.confirm(`Remove ${person.displayName} as a teaching assistant?`)) return
    setActionError(null)
    try {
      await rosterState.removeTa.mutateAsync(person.userId)
    } catch (caught) {
      setActionError(formatUserFacingApiError(caught, 'Unable to remove teaching assistant.'))
    }
  }

  if (!cohortId) return <PeopleState message="Select a cohort to view its people." />
  if (rosterState.query.isLoading) return <PeopleState message="Loading cohort roster…" />
  if (rosterState.query.isError || !roster) {
    return <PeopleState message="Unable to load this cohort roster." error />
  }

  const showStaff = canManagePeople || filter !== 'classmates'
  const showClassmates = canManagePeople || filter !== 'staff'

  return (
    <div className={`people-page ${canManagePeople ? 'owner' : 'learner'}`}>
      <div className="people-toolbar">
        <label>
          <Search />
          <input
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setPage(0)
              setSelectedIds([])
            }}
            placeholder="Search people…"
            aria-label="Search people"
          />
        </label>
        <div className="people-filters">
          {(canManagePeople ? ['all', 'enrolled', 'pending'] : ['all', 'staff', 'classmates']).map(
            (value) => (
              <button
                type="button"
                key={value}
                className={filter === value ? 'active' : undefined}
                onClick={() => {
                  setFilter(value as ManagerFilter | LearnerFilter)
                  setPage(0)
                  setSelectedIds([])
                }}
              >
                {value[0].toUpperCase() + value.slice(1)}
              </button>
            ),
          )}
        </div>
        {canManagePeople ? (
          <div className="people-owner-actions">
            <button
              type="button"
              className="v2-outline-button"
              onClick={() => void copyInvite()}
              disabled={!inviteQuery.data}
            >
              <Link2 />
              {copied ? 'Link copied' : 'Copy invite link'}
            </button>
            <button type="button" className="v2-primary-button" onClick={() => setShowEnroll(true)}>
              <Plus /> Enroll learner
            </button>
          </div>
        ) : null}
      </div>

      {canManagePeople ? (
        <p className="people-summary">
          {formatCount(roster.learnerCount, 'learner')} ·{' '}
          {formatCount(roster.teachingAssistantCount, 'TA')} ·{' '}
          {roster.pendingCount} pending
        </p>
      ) : null}
      {actionError ? <p className="people-inline-error" role="alert">{actionError}</p> : null}

      {showStaff && matchesQuery(roster.instructor) ? (
        <RosterSection title="INSTRUCTOR">
          <PersonRow person={roster.instructor} badge="INSTRUCTOR" subtitle="Course instructor" />
        </RosterSection>
      ) : null}

      {showStaff ? (
        <RosterSection
          title={`TEACHING ASSISTANTS (${roster.teachingAssistantCount})`}
          action={canManagePeople ? (
            <button type="button" onClick={() => setShowAddTa((value) => !value)}>
              <Plus /> Add TA
            </button>
          ) : undefined}
        >
          <div className="add-ta-anchor">
            {roster.teachingAssistants.filter(matchesQuery).map((person) => (
              <PersonRow
                key={person.userId}
                person={person}
                badge="TA"
                onRemove={canManagePeople ? () => removeTeachingAssistant(person) : undefined}
              />
            ))}
            {roster.teachingAssistants.length === 0 ? (
              <p className="people-empty-row">No teaching assistants assigned.</p>
            ) : null}
            {showAddTa ? (
              <AddTaPopover
                candidates={taCandidates}
                onChoose={async (userId) => {
                  setActionError(null)
                  try {
                    await rosterState.addTa.mutateAsync(userId)
                    setShowAddTa(false)
                  } catch (caught) {
                    setActionError(formatUserFacingApiError(caught, 'Unable to add teaching assistant.'))
                  }
                }}
                onClose={() => setShowAddTa(false)}
              />
            ) : null}
          </div>
        </RosterSection>
      ) : null}

      {showClassmates ? (
        <section className="people-roster-section learners-section">
          <h2>{canManagePeople ? `LEARNERS (${visibleLearners.length})` : `CLASSMATES (${visibleLearners.length})`}</h2>
          {canManagePeople ? (
            <ManagerLearnerTable
              people={pagedLearners}
              teachingAssistants={roster.teachingAssistants}
              selected={selected}
              openMenuUserId={openMenuUserId}
              onToggleSelected={(userId) => setSelectedIds((current) =>
                current.includes(userId) ? current.filter((id) => id !== userId) : [...current, userId])}
              onTogglePage={(checked) => setSelectedIds(checked
                ? pagedLearners.filter((person) => person.status === 'ENROLLED').map((person) => person.userId)
                : [])}
              onAssign={(ids, taId) => void assignTa(ids, taId)}
              onOpenMenu={setOpenMenuUserId}
              onRemove={(person) => void removePerson(person)}
            />
          ) : (
            <ClassmateList
              people={visibleLearners}
              currentUserId={currentUserId}
              friendIds={friendIds}
              areFriendsLoading={areFriendsLoading}
              onMessage={(userId) => navigate(`/app/friends?friend=${encodeURIComponent(userId)}`)}
            />
          )}
          {canManagePeople && visibleLearners.length > PAGE_SIZE ? (
            <nav className="people-pagination" aria-label="Roster pagination">
              <span>Page {visiblePage + 1} of {pageCount}</span>
              <button type="button" aria-label="Previous roster page" disabled={visiblePage === 0} onClick={() => setPage(visiblePage - 1)}><ChevronLeft /></button>
              <button type="button" aria-label="Next roster page" disabled={visiblePage + 1 >= pageCount} onClick={() => setPage(visiblePage + 1)}><ChevronRight /></button>
            </nav>
          ) : null}
        </section>
      ) : null}

      {showEnroll ? (
        <EnrollLearnerModal
          cohortName={selectedCohort?.name ?? 'this cohort'}
          email={enrollment.learnerEmail}
          setEmail={enrollment.setLearnerEmail}
          enrollmentError={enrollment.error}
          isEnrolling={enrollment.isSubmitting}
          isInviting={rosterState.invite.isPending}
          onEnroll={async () => {
            if (await enrollment.enroll()) {
              await rosterState.query.refetch()
              setShowEnroll(false)
              enrollment.reset()
            }
          }}
          onInvite={async () => {
            try {
              await rosterState.invite.mutateAsync(enrollment.learnerEmail.trim())
              setShowEnroll(false)
              enrollment.reset()
            } catch (caught) {
              setActionError(formatUserFacingApiError(caught, 'Unable to create invitation.'))
            }
          }}
          onClose={() => {
            setShowEnroll(false)
            enrollment.reset()
          }}
        />
      ) : null}
    </div>
  )
}

function RosterSection({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return <section className="people-roster-section"><h2>{title}{action}</h2><div className="roster-box">{children}</div></section>
}

function PersonRow({ person, subtitle, badge, onRemove }: { person: CohortRosterMember; subtitle?: string; badge: string; onRemove?: () => Promise<void> }) {
  return <div className="person-row"><V2Avatar name={person.displayName} tone={avatarTone(person.userId)} size="md" /><span><strong>{person.displayName}</strong>{subtitle || person.email ? <small>{subtitle ?? person.email}</small> : null}</span><b className={badge === 'TA' ? 'ta' : ''}>{badge}</b>{onRemove ? <button type="button" aria-label={`Remove ${person.displayName} as teaching assistant`} title="Remove teaching assistant" onClick={() => void onRemove()}><UserMinus /></button> : <span />}</div>
}

function ManagerLearnerTable({ people, teachingAssistants, selected, openMenuUserId, onToggleSelected, onTogglePage, onAssign, onOpenMenu, onRemove }: {
  people: CohortRosterMember[]
  teachingAssistants: CohortRosterMember[]
  selected: string[]
  openMenuUserId: string | null
  onToggleSelected: (userId: string) => void
  onTogglePage: (checked: boolean) => void
  onAssign: (learnerIds: string[], teachingAssistantId: string | null) => void
  onOpenMenu: (userId: string | null) => void
  onRemove: (person: CohortRosterMember) => void
}) {
  const selectableIds = people.filter((person) => person.status === 'ENROLLED').map((person) => person.userId)
  return <div className="learner-table">
    <div className={`learner-table-head ${selected.length ? 'selected' : ''}`}>
      <input type="checkbox" aria-label="Select all learners on page" checked={selectableIds.length > 0 && selectableIds.every((id) => selected.includes(id))} onChange={(event) => onTogglePage(event.target.checked)} />
      {selected.length ? <><strong>{selected.length} selected</strong><select aria-label="Assign selected learners" defaultValue="" onChange={(event) => onAssign(selected, event.target.value || null)}><option value="" disabled>Assign TA</option><option value="">Unassigned</option>{teachingAssistants.map((person) => <option value={person.userId} key={person.userId}>{person.displayName}</option>)}</select><button type="button" className="clear-selection" onClick={() => onTogglePage(false)}>Clear selection</button></> : <><span>Name</span><span>Status</span><span>Assigned TA</span><Ellipsis /></>}
    </div>
    {people.map((person) => {
      const assignedTa = teachingAssistants.find((ta) => ta.userId === person.assignedTeachingAssistantUserId)
      return <div className={selected.includes(person.userId) ? 'learner-table-row selected' : 'learner-table-row'} key={`${person.status}-${person.userId}`}>
        <input type="checkbox" aria-label={`Select ${person.displayName}`} disabled={person.status === 'PENDING'} checked={selected.includes(person.userId)} onChange={() => onToggleSelected(person.userId)} />
        <span className="person-name"><V2Avatar name={person.displayName} tone={avatarTone(person.userId)} size="sm" /><span><strong>{person.displayName}</strong>{person.email ? <small>{person.email}</small> : null}</span></span>
        <b className={person.status === 'PENDING' ? 'pending' : ''}>{titleCase(person.status)}</b>
        {person.status === 'ENROLLED' ? <select className="assigned-ta" aria-label={`Assigned TA for ${person.displayName}`} value={assignedTa?.userId ?? ''} onChange={(event) => onAssign([person.userId], event.target.value || null)}><option value="">Unassigned</option>{teachingAssistants.map((ta) => <option value={ta.userId} key={ta.userId}>{ta.displayName}</option>)}</select> : <span className="pending-assignment">Awaiting acceptance</span>}
        <span className="people-row-menu-anchor"><button type="button" className="row-menu" aria-label={`Actions for ${person.displayName}`} onClick={() => onOpenMenu(openMenuUserId === person.userId ? null : person.userId)}><Ellipsis /></button>{openMenuUserId === person.userId ? <span className="people-row-menu"><button type="button" onClick={() => onRemove(person)}>{person.status === 'PENDING' ? <X /> : <Trash2 />}{person.status === 'PENDING' ? 'Cancel invitation' : 'Remove learner'}</button></span> : null}</span>
      </div>
    })}
    {people.length === 0 ? <p className="people-empty-row">No people match this view.</p> : null}
  </div>
}

function ClassmateList({ people, currentUserId, friendIds, areFriendsLoading, onMessage }: { people: CohortRosterMember[]; currentUserId: string | null; friendIds: Set<string>; areFriendsLoading: boolean; onMessage: (userId: string) => void }) {
  if (people.length === 0) return <div className="learner-classmate-list"><p className="people-empty-row">No classmates match this view.</p></div>
  return <div className="learner-classmate-list">{people.map((person) => {
    const isCurrentUser = person.userId === currentUserId
    const canMessage = !isCurrentUser && person.status === 'ENROLLED' && friendIds.has(person.userId)
    return <div className={isCurrentUser ? 'current' : ''} key={`${person.status}-${person.userId}`}><V2Avatar name={person.displayName} tone={avatarTone(person.userId)} size="md" /><strong>{person.displayName}{isCurrentUser ? ' (You)' : ''}</strong>{!isCurrentUser ? <button type="button" aria-label={`Message ${person.displayName}`} disabled={!canMessage || areFriendsLoading} title={canMessage ? 'Open direct message' : 'Become friends before messaging'} onClick={() => onMessage(person.userId)}><MessageCircle /> Message</button> : <span />}</div>
  })}</div>
}

function AddTaPopover({ candidates, onChoose, onClose }: { candidates: CohortRosterMember[]; onChoose: (userId: string) => Promise<void>; onClose: () => void }) {
  const [query, setQuery] = useState('')
  const visible = candidates.filter((person) => person.displayName.toLowerCase().includes(query.toLowerCase()) || Boolean(person.email?.toLowerCase().includes(query.toLowerCase())))
  return <div className="people-popover add-ta-popover"><header><h3>Add teaching assistant</h3><button type="button" aria-label="Close add teaching assistant" onClick={onClose}><X /></button></header><label><Search /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search enrolled learners…" aria-label="Search TA candidates" /></label>{visible.map((person) => <button type="button" key={person.userId} onClick={() => void onChoose(person.userId)}><V2Avatar name={person.displayName} tone={avatarTone(person.userId)} size="sm" /><span><strong>{person.displayName}</strong><small>{person.email ?? 'Enrolled learner'}</small></span></button>)}{visible.length === 0 ? <p className="people-empty-row">No enrolled learners found.</p> : null}</div>
}

function EnrollLearnerModal({ cohortName, email, setEmail, enrollmentError, isEnrolling, isInviting, onEnroll, onInvite, onClose }: { cohortName: string; email: string; setEmail: (value: string) => void; enrollmentError: string | null; isEnrolling: boolean; isInviting: boolean; onEnroll: () => Promise<void>; onInvite: () => Promise<void>; onClose: () => void }) {
  const submit = (event: FormEvent) => { event.preventDefault(); void onEnroll() }
  return <div className="v2-modal-backdrop" role="presentation"><form className="enroll-learner-modal" onSubmit={submit} role="dialog" aria-modal="true" aria-label="Enroll learner"><button type="button" className="modal-close" aria-label="Close enroll learner" onClick={onClose}><X /></button><h2>Enroll learner</h2><p>Add a registered Chanter account to {cohortName}, or create a pending invitation.</p><label>Account email<input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="learner@example.edu" /></label>{enrollmentError ? <p className="modal-error" role="alert">{enrollmentError}</p> : null}<footer><button type="button" className="v2-outline-button" onClick={onClose}>Cancel</button><button type="button" className="v2-outline-button" disabled={!email.trim() || isInviting || isEnrolling} onClick={() => void onInvite()}>{isInviting ? 'Inviting…' : 'Create pending invite'}</button><button type="submit" className="v2-primary-button" disabled={!email.trim() || isEnrolling || isInviting}>{isEnrolling ? 'Enrolling…' : 'Enroll now'}</button></footer></form></div>
}

function PeopleState({ message, error = false }: { message: string; error?: boolean }) {
  return <div className="people-page"><p className={error ? 'people-inline-error' : 'people-empty-row'} role={error ? 'alert' : undefined}>{message}</p></div>
}

function avatarTone(userId: string): AvatarTone {
  const tones: AvatarTone[] = ['blue', 'amber', 'green', 'purple', 'rose']
  const total = Array.from(userId).reduce((sum, character) => sum + character.charCodeAt(0), 0)
  return tones[total % tones.length] ?? 'blue'
}

function formatCount(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`
}

function titleCase(value: string): string {
  return value[0] + value.slice(1).toLowerCase()
}
