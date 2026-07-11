import type { ShellChannel } from '../../types'

import { ContextPanelFrame, ContextWidgetSection } from './ContextPanelFrame'

export function StudyServerContextPanel({ channel }: { channel: ShellChannel }) {
  return (
    <ContextPanelFrame eyebrow="Study Server" title={`#${channel.name}`}>
      <ContextWidgetSection title="Channel">
        <p className="text-xs text-app-muted">
          Server-wide channel for announcements, discussion, and collaboration outside a single
          course.
        </p>
      </ContextWidgetSection>
    </ContextPanelFrame>
  )
}
