import { useQuery, useQueryClient } from '@tanstack/react-query'

import {
  acceptStudyServerInvitation,
  fetchPendingStudyServerInvitations,
} from '../../course-lifecycle/course-lifecycle-api'
import { formatUserFacingApiError } from '../../../lib/format-api-error'

export function HomeStudyServerInvites() {
  const queryClient = useQueryClient()
  const invitesQuery = useQuery({
    queryKey: ['study-server-invitations'],
    queryFn: fetchPendingStudyServerInvitations,
  })

  if (invitesQuery.isLoading || !invitesQuery.data?.length) {
    return null
  }

  const accept = async (studyServerId: string, invitationId: string) => {
    try {
      await acceptStudyServerInvitation(studyServerId, invitationId)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['study-server-invitations'] }),
        queryClient.invalidateQueries({ queryKey: ['study-servers'] }),
      ])
    } catch (caught) {
      window.alert(formatUserFacingApiError(caught, 'Unable to accept invitation.'))
    }
  }

  return (
    <section className="home-invites-panel" aria-label="Study Server invitations">
      <h2>Study Server invitations</h2>
      <ul>
        {invitesQuery.data.map((invite) => (
          <li key={invite.id}>
            <div>
              <strong>{invite.studyServerName}</strong>
              <span>{invite.email}</span>
            </div>
            <button
              type="button"
              className="v2-primary-button"
              onClick={() => void accept(invite.studyServerId, invite.id)}
            >
              Accept
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
