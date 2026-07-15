import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Sprout, UserPlus, X } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { NavLink, Outlet, useParams } from 'react-router-dom'

import { courseCatalogQueryKey, fetchCourseCatalog } from '../../course-discovery/course-discovery-api'
import {
  communityMemberSummaryQueryKey,
  createStudyServerInvitations,
  fetchStudyServerMemberSummary,
} from '../../community-members/community-members-api'
import { formatUserFacingApiError } from '../../../lib/format-api-error'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { V2Avatar } from '../components/V2Avatar'
import { v2CommunityPath, type V2CommunityTab } from '../v2-routes'
import type { V2CommunityContext } from './v2-community-context'

const tabs: { id: V2CommunityTab; label: string }[] = [
  { id: 'announcements', label: 'Announcements' },
  { id: 'lounge', label: 'Lounge' },
  { id: 'events', label: 'Events' },
  { id: 'discover', label: 'Discover courses' },
  { id: 'members', label: 'Members' },
]

function parseInviteEmails(raw: string): string[] {
  return raw
    .split(/[\s,;]+/)
    .map((value) => value.trim())
    .filter(Boolean)
}

export function V2CommunityHubLayout() {
  const { serverId = 'server-demo' } = useParams()
  const queryClient = useQueryClient()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const catalogQuery = useQuery({
    queryKey: courseCatalogQueryKey(serverId, '', 'ALL'),
    queryFn: () => fetchCourseCatalog(serverId, { search: '', filter: 'ALL' }),
    enabled: serverId !== 'server-demo',
  })
  const memberSummaryQuery = useQuery({
    queryKey: communityMemberSummaryQueryKey(serverId),
    queryFn: () => fetchStudyServerMemberSummary(serverId),
    enabled: serverId !== 'server-demo',
  })
  const [inviteOpen, setInviteOpen] = useState(false)
  const [inviteEmails, setInviteEmails] = useState('')
  const [inviteError, setInviteError] = useState<string | null>(null)
  const [inviteSuccess, setInviteSuccess] = useState<string | null>(null)

  const inviteMutation = useMutation({
    mutationFn: (emails: string[]) => createStudyServerInvitations(serverId, emails),
    onSuccess: async (created) => {
      setInviteError(null)
      setInviteSuccess(
        created.length === 1
          ? `Invite sent to ${created[0]?.email}. They can accept it from Home.`
          : `Invites sent to ${created.length} people. They can accept from Home.`,
      )
      setInviteEmails('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: communityMemberSummaryQueryKey(serverId) }),
        queryClient.invalidateQueries({ queryKey: ['community-members', serverId] }),
        queryClient.invalidateQueries({ queryKey: ['study-server-invitations'] }),
      ])
    },
    onError: (error) => {
      setInviteSuccess(null)
      setInviteError(formatUserFacingApiError(error, 'Unable to send invitations.'))
    },
  })

  const serverName = navigationQuery.data?.studyServerName ?? 'Spring Bootcamp Hub'
  const courseCount = catalogQuery.data?.courses.length ?? navigationQuery.data?.courses.length ?? 0
  const memberCount = memberSummaryQuery.data?.memberCount
  const preview = memberSummaryQuery.data?.preview ?? []
  const overflow = Math.max(0, (memberCount ?? 0) - preview.length)
  const context: V2CommunityContext = {
    serverId,
    serverName,
    isOwner: navigationQuery.data?.capabilities.owner ?? false,
    studyServerCapabilities: navigationQuery.data?.capabilities,
    navigation: navigationQuery.data,
  }

  const submitInvite = (event: FormEvent) => {
    event.preventDefault()
    const emails = parseInviteEmails(inviteEmails)
    if (emails.length === 0) {
      setInviteError('Enter at least one email address.')
      return
    }
    void inviteMutation.mutateAsync(emails)
  }

  return (
    <section className="v2-workspace-page community-hub-page">
      <header className="community-hub-chrome">
        <div className="community-hub-title">
          <span>
            <Sprout />
          </span>
          <div>
            <h1>{serverName}</h1>
            <p>
              Community · {memberCount == null ? '…' : `${memberCount} members`} · {courseCount} courses
            </p>
            {context.studyServerCapabilities?.canManageCommunity ? (
              <button
                type="button"
                className="v2-primary-button"
                onClick={() => {
                  setInviteOpen(true)
                  setInviteError(null)
                  setInviteSuccess(null)
                }}
              >
                <UserPlus />
                Invite people
              </button>
            ) : null}
          </div>
          <div className="community-member-stack">
            {preview.map((member, index) => (
              <V2Avatar
                key={member.userId}
                name={member.displayName}
                tone={index % 2 ? 'purple' : 'amber'}
                size="lg"
              />
            ))}
            {overflow > 0 ? <i>+{overflow}</i> : null}
          </div>
        </div>
        <nav className="community-tabs">
          {tabs.map((tab) => (
            <NavLink key={tab.id} to={v2CommunityPath(serverId, tab.id)}>
              {tab.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <div className="community-tab-panel">
        <Outlet context={context} />
      </div>
      {inviteOpen ? (
        <div className="v2-modal-backdrop" role="presentation">
          <form className="create-event-modal" onSubmit={submitInvite}>
            <button
              type="button"
              className="modal-close"
              aria-label="Close invite dialog"
              onClick={() => setInviteOpen(false)}
            >
              <X />
            </button>
            <h3>Invite people</h3>
            <p>Invite registered Chanter users by email. They’ll see the invite on Home and can accept there.</p>
            <label>
              Emails
              <textarea
                value={inviteEmails}
                onChange={(event) => setInviteEmails(event.target.value)}
                placeholder="teammate@school.edu, friend@school.edu"
                rows={4}
              />
            </label>
            {inviteError ? (
              <p className="v2-inline-error" role="alert">
                {inviteError}
              </p>
            ) : null}
            {inviteSuccess ? <p>{inviteSuccess}</p> : null}
            <footer>
              <button type="button" onClick={() => setInviteOpen(false)}>
                Close
              </button>
              <button
                type="submit"
                className="v2-primary-button"
                disabled={inviteMutation.isPending}
              >
                Send invites
              </button>
            </footer>
          </form>
        </div>
      ) : null}
    </section>
  )
}
