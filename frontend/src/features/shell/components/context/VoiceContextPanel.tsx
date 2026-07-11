import { Link } from 'react-router-dom'

import { ContextPanelFrame, ContextWidgetSection } from './ContextPanelFrame'

export function VoiceContextPanel({
  serverId,
  courseId,
  channelLabel,
}: {
  serverId: string
  courseId?: string
  channelLabel: string
}) {
  return (
    <ContextPanelFrame eyebrow="Voice" title={`#${channelLabel}`}>
      <ContextWidgetSection title="Connected members">
        <p className="text-xs text-app-muted">
          Join from the main panel to talk with classmates. Mute and deafen controls appear after you
          connect.
        </p>
      </ContextWidgetSection>

      {courseId ? (
        <ContextWidgetSection title="Office hours">
          <p className="text-xs text-app-muted">
            Check scheduled office hours for instructor-led voice sessions.
          </p>
          <Link
            to={`/app/servers/${serverId}/courses/${courseId}/support/office-hours`}
            className="mt-2 inline-flex text-xs font-medium text-app-accent hover:text-app-accent-hover"
          >
            Open office hours
          </Link>
        </ContextWidgetSection>
      ) : null}
    </ContextPanelFrame>
  )
}
