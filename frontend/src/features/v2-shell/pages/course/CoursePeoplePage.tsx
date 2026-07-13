import { useMemo, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Check, ChevronDown, Ellipsis, Link2, MessageCircle, Plus, Search, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { useCohortEnrollment } from '../../../onboarding/hooks/use-cohort-enrollment'
import { useCohortEnrollments, useCohortInvite } from '../../../onboarding/hooks/use-cohort-enrollments'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2CourseWorkspace } from '../../layouts/v2-course-workspace-context'

const classmates = [
  { id: 'sam', name: 'Sam Chen', tone: 'blue' as const, status: 'Enrolled', ta: 'Maria Gonzalez' },
  { id: 'priya', name: 'Priya Patel', tone: 'amber' as const, status: 'Enrolled', ta: 'Jordan Kim' },
  { id: 'daniel', name: 'Daniel Anderson', tone: 'green' as const, status: 'Pending', ta: 'Unassigned' },
  { id: 'maya', name: 'Maya Gonzalez', tone: 'purple' as const, status: 'Enrolled', ta: 'Maria Gonzalez' },
  { id: 'noah', name: 'Noah Smith', tone: 'blue' as const, status: 'Enrolled', ta: 'Unassigned' },
]

const assistants = [
  { name: 'Maria Gonzalez', tone: 'purple' as const },
  { name: 'Jordan Kim', tone: 'blue' as const },
]

export function CoursePeoplePage() {
  const { course, isOwner } = useV2CourseWorkspace()
  const cohortId = course.cohorts[0]?.id
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState(isOwner ? 'all' : 'all')
  const [selected, setSelected] = useState<string[]>([])
  const [showAddTa, setShowAddTa] = useState(false)
  const [showAssign, setShowAssign] = useState(false)
  const [showEnroll, setShowEnroll] = useState(false)
  const [copied, setCopied] = useState(false)
  const enrollment = useCohortEnrollment(cohortId ?? '')
  const enrollmentQuery = useCohortEnrollments(isOwner ? cohortId : undefined, { limit: 50, search: query || undefined })
  const inviteQuery = useCohortInvite(isOwner ? cohortId : undefined)

  const visibleClassmates = useMemo(() => classmates.filter((person) => {
    const matchesQuery = person.name.toLowerCase().includes(query.toLowerCase())
    const matchesFilter = !isOwner || filter === 'all' || person.status.toLowerCase() === filter
    return matchesQuery && matchesFilter
  }), [filter, isOwner, query])

  const copyInvite = async () => {
    if (!cohortId || !inviteQuery.data) return
    const params = new URLSearchParams({ cohort: cohortId, invite: inviteQuery.data.inviteCode })
    await navigator.clipboard.writeText(`${window.location.origin}/sign-in?${params.toString()}`)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1800)
  }

  const enroll = async (event: FormEvent) => {
    event.preventDefault()
    if (await enrollment.enroll()) {
      await queryClient.invalidateQueries({ queryKey: ['cohort-enrollments', cohortId] })
      setShowEnroll(false)
      enrollment.reset()
    }
  }

  return (
    <div className={`people-page ${isOwner ? 'owner' : 'learner'}`}>
      <div className="people-toolbar">
        <label><Search /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search people…" /></label>
        <div className="people-filters">{(isOwner ? ['all','enrolled','pending'] : ['all','online','staff']).map((value) => <button type="button" key={value} className={filter === value ? 'active' : undefined} onClick={() => setFilter(value)}>{value[0].toUpperCase() + value.slice(1)}</button>)}</div>
        {isOwner ? <div className="people-owner-actions"><button type="button" className="v2-outline-button" onClick={() => void copyInvite()}><Link2 />{copied ? 'Link copied' : 'Copy invite link'}</button><button type="button" className="v2-primary-button" onClick={() => setShowEnroll(true)}><Plus />Enroll learner</button></div> : null}
      </div>
      {isOwner ? <p className="people-summary">{enrollmentQuery.data?.totalCount ?? 24} learners · 2 TAs · 1 pending</p> : null}

      <RosterSection title="INSTRUCTOR"><PersonRow name="Dr. Alex Johnson" tone="amber" subtitle="Course instructor" badge="OWNER" learnerView={!isOwner} /></RosterSection>
      <RosterSection title="TEACHING ASSISTANTS" action={isOwner ? <button type="button" onClick={() => setShowAddTa((value) => !value)}><Plus />Add TA</button> : null}>
        <div className="add-ta-anchor">{assistants.map((person) => <PersonRow key={person.name} name={person.name} tone={person.tone} badge="TA" learnerView={!isOwner} menu={isOwner} />)}{showAddTa ? <AddTaPopover onChoose={() => setShowAddTa(false)} /> : null}</div>
      </RosterSection>

      <section className="people-roster-section learners-section">
        <h2>{isOwner ? 'LEARNERS (24)' : 'CLASSMATES (18)'}</h2>
        {isOwner ? (
          <div className="learner-table">
            <div className={`learner-table-head ${selected.length ? 'selected' : ''}`}>
              <input type="checkbox" checked={selected.length === visibleClassmates.length && visibleClassmates.length > 0} onChange={(event) => setSelected(event.target.checked ? visibleClassmates.map((person) => person.id) : [])} />
              {selected.length ? <><strong>{selected.length} selected</strong><div className="assign-ta-anchor"><button type="button" onClick={() => setShowAssign((value) => !value)}>Assign TA <ChevronDown /></button>{showAssign ? <AssignTaPopover onChoose={() => { setShowAssign(false); setSelected([]) }} /> : null}</div><button type="button" className="clear-selection" onClick={() => setSelected([])}>Clear selection</button></> : <><span>Name</span><span>Status</span><span>Assigned TA</span><Ellipsis /></>}
            </div>
            {visibleClassmates.map((person) => <div className={selected.includes(person.id) ? 'learner-table-row selected' : 'learner-table-row'} key={person.id}><input type="checkbox" checked={selected.includes(person.id)} onChange={() => setSelected((items) => items.includes(person.id) ? items.filter((id) => id !== person.id) : [...items,person.id])} /><span className="person-name"><V2Avatar name={person.name} tone={person.tone} size="sm" /><strong>{person.name}</strong></span><b className={person.status === 'Pending' ? 'pending' : ''}>{person.status}</b><button type="button" className="assigned-ta">{person.ta}<ChevronDown /></button><button type="button" className="row-menu"><Ellipsis /></button></div>)}
            {(enrollmentQuery.data?.enrollments ?? []).slice(0,3).map((record) => <div className="learner-table-row" key={record.learnerUserId}><input type="checkbox" /><span className="person-name"><V2Avatar name={record.learnerUserId} tone="blue" size="sm" /><strong>{record.learnerUserId.slice(0,8)}…</strong></span><b>Enrolled</b><button type="button" className="assigned-ta">Unassigned<ChevronDown /></button><button type="button" className="row-menu"><Ellipsis /></button></div>)}
          </div>
        ) : <div className="learner-classmate-list">{visibleClassmates.map((person,index) => <div className={index === 0 ? 'current' : ''} key={person.id}><V2Avatar name={person.name} tone={person.tone} size="md" online={person.status === 'Enrolled'} /><strong>{person.name}{index === 0 ? ' (You)' : ''}</strong><button type="button" onClick={() => navigate('/app/friends')}><MessageCircle />Message</button></div>)}<button type="button" className="more-people">+13 more</button></div>}
      </section>

      {showEnroll ? <EnrollLearnerModal enrollment={enrollment} onSubmit={enroll} onClose={() => { setShowEnroll(false); enrollment.reset() }} /> : null}
    </div>
  )
}

function RosterSection({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) { return <section className="people-roster-section"><h2>{title}{action}</h2><div className="roster-box">{children}</div></section> }
function PersonRow({ name, tone, subtitle, badge, learnerView, menu }: { name: string; tone: 'blue'|'amber'|'green'|'purple'; subtitle?: string; badge: string; learnerView: boolean; menu?: boolean }) { return <div className="person-row"><V2Avatar name={name} tone={tone} size="md" online /><span><strong>{name}</strong>{subtitle ? <small>{subtitle}</small> : null}</span><b className={badge === 'TA' ? 'ta' : ''}>{badge}</b>{menu ? <button type="button"><Ellipsis /></button> : learnerView ? null : null}</div> }
function AddTaPopover({ onChoose }: { onChoose: () => void }) { return <div className="people-popover add-ta-popover"><h3>Add teaching assistant</h3><label><Search /><input placeholder="Search members…" /></label>{[['Priya Patel','Learner · CS 101'],['Daniel Anderson','Learner · CS 101'],['Noah Smith','Learner · CS 101'],['Marcus Webb','Hub member']].map(([name,detail],index) => <button type="button" key={name} className={index === 0 ? 'active' : ''} onClick={onChoose}><V2Avatar name={name} tone={index % 2 ? 'green' : 'amber'} size="sm" /><span><strong>{name}</strong><small>{detail}</small></span></button>)}</div> }
function AssignTaPopover({ onChoose }: { onChoose: () => void }) { return <div className="people-popover assign-ta-popover">{['Maria Gonzalez','Jordan Kim','Unassigned'].map((name,index) => <button type="button" className={index === 0 ? 'active' : ''} key={name} onClick={onChoose}>{name}{index === 0 ? <Check /> : null}</button>)}</div> }
type EnrollmentHook = ReturnType<typeof useCohortEnrollment>
function EnrollLearnerModal({ enrollment, onSubmit, onClose }: { enrollment: EnrollmentHook; onSubmit: (event: FormEvent) => void; onClose: () => void }) { const [assignTa,setAssignTa]=useState(false); return <div className="v2-modal-backdrop"><form className="enroll-learner-modal" onSubmit={onSubmit}><button type="button" className="modal-close" onClick={onClose}><X /></button><h2>Enroll learner</h2><p>Add a learner directly to the Spring cohort.</p><label>Email or user ID<input value={enrollment.learnerUserId} onChange={(event) => enrollment.setLearnerUserId(event.target.value)} placeholder="learner@example.edu" /></label><label className="assign-on-enroll"><input type="checkbox" checked={assignTa} onChange={(event) => setAssignTa(event.target.checked)} />Assign a teaching assistant after enrollment</label>{enrollment.error ? <p className="modal-error">{enrollment.error}</p> : null}<footer><button type="button" className="v2-outline-button" onClick={onClose}>Cancel</button><button type="submit" className="v2-primary-button" disabled={enrollment.isSubmitting}>{enrollment.isSubmitting ? 'Enrolling…' : 'Enroll learner'}</button></footer></form></div> }
