import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  ChevronRight,
  Heart,
  MessageCircle,
  Plus,
  Search,
  Send,
  Smile,
  Sprout,
  UsersRound,
  Hash,
  Volume2,
  X,
} from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import {
  archiveCommunityAnnouncement,
  communityAnnouncementsQueryKey,
  createCommunityAnnouncement,
  fetchCommunityAnnouncements,
  updateCommunityAnnouncement,
  upsertCommunityAnnouncementLike,
} from '../../../community-announcements/community-announcements-api'
import type { CommunityAnnouncement } from '../../../community-announcements/community-announcement-types'
import {
  communityMembersQueryKey,
  fetchStudyServerMemberSummary,
  fetchStudyServerMembers,
  communityMemberSummaryQueryKey,
} from '../../../community-members/community-members-api'
import type { StudyServerMember, StudyServerMemberFilter } from '../../../community-members/community-member-types'
import { formatUserFacingApiError } from '../../../../lib/format-api-error'
import { useAcceptedFriendIds } from '../../../people/use-accepted-friend-ids'
import { useChannelConversation } from '../../../shell/hooks/use-channel-conversation'
import { useAuthStore } from '../../../../stores/auth-store'
import { V2Avatar } from '../../components/V2Avatar'
import { useV2Community } from '../../layouts/v2-community-context'
import { v2CommunityPath } from '../../v2-routes'

export { CommunityEventsPage } from './CommunityEventsPage'
export { CommunityDiscoverPage } from './CommunityDiscoverPage'

type AvatarTone = 'blue' | 'green' | 'amber' | 'purple'

function avatarTone(seed: string): AvatarTone {
  const tones: AvatarTone[] = ['blue', 'amber', 'green', 'purple']
  let hash = 0
  for (let index = 0; index < seed.length; index += 1) {
    hash = (hash + seed.charCodeAt(index) * (index + 1)) % tones.length
  }
  return tones[hash] ?? 'blue'
}

function formatRelativeAge(iso: string): string {
  const created = new Date(iso).getTime()
  const deltaMs = Date.now() - created
  const minutes = Math.max(0, Math.floor(deltaMs / 60_000))
  if (minutes < 60) return `${minutes || 1}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 48) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

export function CommunityAnnouncementsPage() {
  const { serverId, studyServerCapabilities, navigation, serverName } = useV2Community()
  const canManage = studyServerCapabilities?.canManageCommunity ?? false
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<CommunityAnnouncement | null | undefined>(undefined)
  const [actionError, setActionError] = useState<string | null>(null)

  const announcementsQuery = useQuery({
    queryKey: communityAnnouncementsQueryKey(serverId),
    queryFn: () => fetchCommunityAnnouncements(serverId),
    enabled: Boolean(serverId),
  })
  const summaryQuery = useQuery({
    queryKey: communityMemberSummaryQueryKey(serverId),
    queryFn: () => fetchStudyServerMemberSummary(serverId),
    enabled: Boolean(serverId),
  })
  const eventsQuery = useQuery({
    queryKey: ['community-events', serverId, 'UPCOMING'],
    queryFn: async () => {
      const { fetchCommunityEvents } = await import('../../../community-events/community-events-api')
      return fetchCommunityEvents(serverId, 'UPCOMING')
    },
    enabled: Boolean(serverId),
  })

  const announcements = announcementsQuery.data?.announcements ?? []
  const upcoming = (eventsQuery.data?.events ?? []).slice(0, 3)
  const courseCount = navigation?.courses.length ?? 0
  const memberCount = summaryQuery.data?.memberCount

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['community-announcements', serverId] })
  }

  const likeMutation = useMutation({
    mutationFn: ({ id, liked }: { id: string; liked: boolean }) =>
      upsertCommunityAnnouncementLike(serverId, id, liked),
    onSuccess: async () => {
      setActionError(null)
      await invalidate()
    },
    onError: (error) => setActionError(formatUserFacingApiError(error, 'Unable to update reaction.')),
  })

  const saveMutation = useMutation({
    mutationFn: async ({
      announcementId,
      title,
      body,
    }: {
      announcementId?: string
      title: string
      body: string
    }) => {
      if (announcementId) {
        return updateCommunityAnnouncement(serverId, announcementId, { title, body })
      }
      return createCommunityAnnouncement(serverId, { title, body })
    },
    onSuccess: async () => {
      setEditing(undefined)
      setActionError(null)
      await invalidate()
    },
    onError: (error) => setActionError(formatUserFacingApiError(error, 'Unable to save announcement.')),
  })

  const archiveMutation = useMutation({
    mutationFn: (announcementId: string) => archiveCommunityAnnouncement(serverId, announcementId),
    onSuccess: async () => {
      setActionError(null)
      await invalidate()
    },
    onError: (error) => setActionError(formatUserFacingApiError(error, 'Unable to archive announcement.')),
  })

  return (
    <div className="announcements-layout">
      <main>
        <div className="announcements-toolbar">
          <h2>Announcements</h2>
          {canManage ? (
            <button type="button" className="v2-primary-button" onClick={() => setEditing(null)}>
              <Plus /> Publish
            </button>
          ) : null}
        </div>
        {actionError ? <p className="v2-inline-error" role="alert">{actionError}</p> : null}
        {announcementsQuery.isLoading ? <p>Loading announcements…</p> : null}
        {announcementsQuery.isError ? (
          <p className="v2-inline-error" role="alert">
            {formatUserFacingApiError(announcementsQuery.error, 'Unable to load announcements.')}
          </p>
        ) : null}
        {!announcementsQuery.isLoading && announcements.length === 0 ? (
          <p className="announcements-empty">No published announcements yet.</p>
        ) : null}
        {announcements.map((item) => (
          <article className="announcement-card" key={item.id}>
            <V2Avatar name={item.authorDisplayName} tone={avatarTone(item.authorUserId)} size="lg" />
            <div>
              <p>
                <strong>{item.authorDisplayName}</strong>
                <time>{formatRelativeAge(item.createdAt)}</time>
                {item.canEdit ? (
                  <>
                    <button type="button" onClick={() => setEditing(item)}>
                      Edit
                    </button>
                    <button
                      type="button"
                      onClick={() => void archiveMutation.mutateAsync(item.id)}
                      disabled={archiveMutation.isPending}
                    >
                      Archive
                    </button>
                  </>
                ) : null}
              </p>
              <h2>{item.title}</h2>
              <span>{item.body}</span>
            </div>
            <footer>
              <button
                type="button"
                aria-pressed={item.viewerLiked}
                onClick={() =>
                  void likeMutation.mutateAsync({ id: item.id, liked: !item.viewerLiked })
                }
                disabled={likeMutation.isPending}
              >
                <Heart />
                {item.likeCount}
              </button>
            </footer>
          </article>
        ))}
      </main>
      <aside>
        <section>
          <h2>
            <Sprout />
            About
          </h2>
          <p>{serverName}</p>
          <p>
            <UsersRound />
            {courseCount} courses
          </p>
          <p>
            <UsersRound />
            {memberCount == null ? '…' : `${memberCount} members`}
          </p>
        </section>
        <section>
          <h2>
            <CalendarDays />
            Upcoming events
          </h2>
          {upcoming.length === 0 ? <p>No upcoming events.</p> : null}
          {upcoming.map((event) => (
            <p className="upcoming-community-event" key={event.id}>
              <CalendarDays />
              <span>
                <strong>{event.title}</strong>
                <small>{new Date(event.startsAt).toLocaleString()}</small>
              </span>
            </p>
          ))}
          <Link to={v2CommunityPath(serverId, 'events')}>
            View all events <ChevronRight />
          </Link>
        </section>
      </aside>
      {editing !== undefined ? (
        <AnnouncementEditorModal
          initial={editing}
          isSaving={saveMutation.isPending}
          onClose={() => setEditing(undefined)}
          onSave={(title, body) =>
            void saveMutation.mutateAsync({
              announcementId: editing?.id,
              title,
              body,
            })
          }
        />
      ) : null}
    </div>
  )
}

function AnnouncementEditorModal({
  initial,
  isSaving,
  onClose,
  onSave,
}: {
  initial: CommunityAnnouncement | null
  isSaving: boolean
  onClose: () => void
  onSave: (title: string, body: string) => void
}) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [body, setBody] = useState(initial?.body ?? '')
  return (
    <div className="v2-modal-backdrop" role="presentation">
      <form
        className="create-event-modal"
        onSubmit={(event) => {
          event.preventDefault()
          onSave(title.trim(), body.trim())
        }}
      >
        <button type="button" className="modal-close" aria-label="Close" onClick={onClose}>
          <X />
        </button>
        <h3>{initial ? 'Edit announcement' : 'Publish announcement'}</h3>
        <label>
          Title
          <input value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={200} />
        </label>
        <label>
          Body
          <textarea value={body} onChange={(event) => setBody(event.target.value)} required maxLength={8000} rows={6} />
        </label>
        <footer>
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="v2-primary-button" disabled={isSaving || !title.trim() || !body.trim()}>
            {initial ? 'Save' : 'Publish'}
          </button>
        </footer>
      </form>
    </div>
  )
}

export function CommunityLoungePage() {
  const { navigation } = useV2Community()
  const textChannels = navigation?.studyServerChannels.filter((channel) => channel.kind === 'TEXT') ?? []
  const voiceChannels = navigation?.studyServerChannels.filter((channel) => channel.kind === 'VOICE') ?? []
  const [selectedId, setSelectedId] = useState(textChannels[0]?.id ?? 'lounge-demo')
  const selected = textChannels.find((channel) => channel.id === selectedId) ?? textChannels[0]
  const conversation = useChannelConversation('study', selected?.id)
  const [draft, setDraft] = useState('')
  const submit = (event: FormEvent) => {
    event.preventDefault()
    void conversation.sendMessage(draft).then((sent) => sent && setDraft(''))
  }
  const demo = [
    ['Priya Patel', 'Anyone else stuck on problem set 2?', 'ADMIN', 'purple'],
    ['Marcus Webb', 'Welcome to the hub — say hi in #introductions', 'ADMIN', 'green'],
    ['Maria Gonzalez', 'Study room has 3 people if you want to pair', 'TA', 'amber'],
  ] as const
  return (
    <div className="community-lounge-layout">
      <aside>
        <h2>CHANNELS</h2>
        {(textChannels.length
          ? textChannels
          : [
              { id: 'lounge-demo', name: 'lounge', kind: 'TEXT' as const },
              { id: 'general-demo', name: 'general', kind: 'TEXT' as const },
              { id: 'intro-demo', name: 'introductions', kind: 'TEXT' as const },
              { id: 'off-demo', name: 'off-topic', kind: 'TEXT' as const },
            ]
        ).map((channel) => (
          <button
            type="button"
            key={channel.id}
            className={(selected?.id ?? 'lounge-demo') === channel.id ? 'active' : undefined}
            onClick={() => setSelectedId(channel.id)}
          >
            <Hash />
            {channel.name}
          </button>
        ))}
        <hr />
        <h2>VOICE</h2>
        {(voiceChannels.length
          ? voiceChannels
          : [{ id: 'voice-demo', name: 'Community Lounge', kind: 'VOICE' as const }]
        ).map((channel) => (
          <button type="button" className="community-voice" key={channel.id}>
            <Volume2 />
            {channel.name}
          </button>
        ))}
      </aside>
      <section>
        <div className="community-message-list">
          {conversation.messages.length
            ? conversation.messages.map((message, index) => (
                <CommunityMessage
                  key={message.id}
                  name={`Member ${index + 1}`}
                  body={message.body}
                  role=""
                  tone="blue"
                />
              ))
            : demo.map(([name, body, role, tone]) => (
                <CommunityMessage key={name} name={name} body={body} role={role} tone={tone} />
              ))}
        </div>
        <form onSubmit={submit}>
          <button type="button">
            <Plus />
          </button>
          <input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder={`Message #${selected?.name ?? 'lounge'}`}
          />
          <button type="button">
            <Smile />
          </button>
          <button type="submit" className="send">
            <Send />
          </button>
        </form>
      </section>
    </div>
  )
}

function CommunityMessage({
  name,
  body,
  role,
  tone,
}: {
  name: string
  body: string
  role: string
  tone: AvatarTone
}) {
  return (
    <article className="community-message">
      <V2Avatar name={name} tone={tone} size="lg" />
      <div>
        <p>
          <strong>{name}</strong>
          {role ? <b>{role}</b> : null}
          <time>2:14 PM</time>
        </p>
        <span>{body}</span>
      </div>
    </article>
  )
}

const MEMBER_FILTERS: { label: string; value: StudyServerMemberFilter | 'DISABLED' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Active', value: 'DISABLED' },
  { label: 'Online', value: 'DISABLED' },
  { label: 'Staff', value: 'STAFF' },
  { label: 'Learners', value: 'LEARNERS' },
]

export function CommunityMembersPage() {
  const { serverId } = useV2Community()
  const navigate = useNavigate()
  const currentUserId = useAuthStore((state) => state.user?.id ?? null)
  const { friendIds, isLoading: areFriendsLoading } = useAcceptedFriendIds()
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<StudyServerMemberFilter>('ALL')
  const [page, setPage] = useState(0)
  const pageSize = 40

  const membersQuery = useQuery({
    queryKey: communityMembersQueryKey(serverId, query, filter),
    queryFn: () =>
      fetchStudyServerMembers(serverId, {
        search: query.trim() || undefined,
        filter,
        limit: pageSize,
        offset: page * pageSize,
      }),
    enabled: Boolean(serverId),
  })

  const members = membersQuery.data?.members ?? []
  const staff = useMemo(() => members.filter((member) => member.staff), [members])
  const learners = useMemo(() => members.filter((member) => !member.staff), [members])
  const filteredTotal = membersQuery.data?.filteredTotal ?? 0
  const pageCount = Math.max(1, Math.ceil(filteredTotal / pageSize))

  return (
    <div className="community-members-page">
      <label>
        <Search />
        <input
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setPage(0)
          }}
          placeholder="Search members…"
        />
      </label>
      <div className="member-filters">
        {MEMBER_FILTERS.map((item) => (
          <button
            type="button"
            key={item.label}
            className={
              item.value !== 'DISABLED' && filter === item.value ? 'active' : undefined
            }
            disabled={item.value === 'DISABLED'}
            title={
              item.value === 'DISABLED'
                ? 'Presence filters are not available yet'
                : undefined
            }
            onClick={() => {
              if (item.value === 'DISABLED') return
              setFilter(item.value)
              setPage(0)
            }}
          >
            {item.label}
          </button>
        ))}
      </div>
      {membersQuery.isLoading ? <p>Loading members…</p> : null}
      {membersQuery.isError ? (
        <p className="v2-inline-error" role="alert">
          {formatUserFacingApiError(membersQuery.error, 'Unable to load members.')}
        </p>
      ) : null}
      {!membersQuery.isLoading && members.length === 0 ? <p>No members match this view.</p> : null}
      {staff.length > 0 ? (
        <>
          <h2>STAFF</h2>
          {staff.map((member) => (
            <MemberRow
              key={member.userId}
              member={member}
              current={member.userId === currentUserId}
              canMessage={friendIds.has(member.userId)}
              areFriendsLoading={areFriendsLoading}
              onMessage={() =>
                navigate(`/app/friends?friend=${encodeURIComponent(member.userId)}`)
              }
            />
          ))}
        </>
      ) : null}
      {learners.length > 0 ? (
        <>
          <h2>LEARNERS</h2>
          {learners.map((member) => (
            <MemberRow
              key={member.userId}
              member={member}
              current={member.userId === currentUserId}
              canMessage={friendIds.has(member.userId)}
              areFriendsLoading={areFriendsLoading}
              onMessage={() =>
                navigate(`/app/friends?friend=${encodeURIComponent(member.userId)}`)
              }
            />
          ))}
        </>
      ) : null}
      {filteredTotal > pageSize ? (
        <nav className="member-pagination" aria-label="Members pagination">
          <span>
            Page {page + 1} of {pageCount}
          </span>
          <button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
            Previous
          </button>
          <button
            type="button"
            disabled={page + 1 >= pageCount}
            onClick={() => setPage((current) => current + 1)}
          >
            Next
          </button>
        </nav>
      ) : null}
    </div>
  )
}

function MemberRow({
  member,
  current,
  canMessage,
  areFriendsLoading,
  onMessage,
}: {
  member: StudyServerMember
  current?: boolean
  canMessage: boolean
  areFriendsLoading: boolean
  onMessage: () => void
}) {
  return (
    <article className={current ? 'current' : undefined}>
      <V2Avatar name={member.displayName} tone={avatarTone(member.userId)} size="md" />
      <strong>
        {member.displayName}
        {current ? ' (You)' : ''}
      </strong>
      {member.role ? <b>{member.role}</b> : null}
      {!current ? (
        <button
          type="button"
          disabled={!canMessage || areFriendsLoading}
          title={canMessage ? 'Open direct message' : 'Become friends before messaging'}
          onClick={onMessage}
        >
          <MessageCircle />
          Message
        </button>
      ) : null}
    </article>
  )
}
