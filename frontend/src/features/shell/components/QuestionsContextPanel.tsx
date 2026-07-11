import { useQuery } from '@tanstack/react-query'
import { useLocation, useParams } from 'react-router-dom'

import { Button } from '../../../components/ui/button'
import { useAuthStore } from '../../../stores/auth-store'
import { fetchStudyAssistantPresence } from '../../questions/questions-api'
import { StudyAssistantInstallDialog } from '../../study-assistant/components/StudyAssistantInstallDialog'
import {
  studyAssistantPresenceQueryKey,
  useStudyAssistantInstallFlow,
} from '../../study-assistant/hooks/use-study-assistant-install'
import { grantLabel } from '../../study-assistant/study-assistant-grants'
import { useQuestionsPanel } from '../context/use-questions-panel'
import { useStudyServerNavigationQuery } from '../hooks/use-shell-queries'
import {
  findCourseChannelContext,
  findStudyChannel,
  resolveShellContextPanelKind,
} from '../shell-routes'

import { ContextPlaceholder } from './ContextPlaceholder'
import { GeneralContextPanel } from './context/GeneralContextPanel'
import { ResourcesContextPanel } from './context/ResourcesContextPanel'
import { VoiceContextPanel } from './context/VoiceContextPanel'
import { ContextPanelFrame, ContextWidgetSection } from './context/ContextPanelFrame'
import { ApprovedFaqsWidget } from './context/widgets/ApprovedFaqsWidget'
import { CourseResourcesWidget } from './context/widgets/CourseResourcesWidget'

export function ShellContextPanel() {
  const { serverId, channelId } = useParams()
  const location = useLocation()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const panelKind = resolveShellContextPanelKind(
    location.pathname,
    channelId,
    navigationQuery.data,
  )

  if (!serverId || !channelId || panelKind === 'placeholder') {
    return <ContextPlaceholder />
  }

  if (panelKind === 'questions') {
    return <QuestionsContextPanel studyServerId={serverId} channelId={channelId} />
  }

  const courseContext = findCourseChannelContext(navigationQuery.data, channelId)

  if (panelKind === 'resources' && courseContext) {
    return <ResourcesContextPanel serverId={serverId} course={courseContext.course} />
  }

  if (panelKind === 'general' && courseContext) {
    return <GeneralContextPanel serverId={serverId} course={courseContext.course} />
  }

  if (panelKind === 'voice') {
    const studyChannel = findStudyChannel(navigationQuery.data, channelId)
    return (
      <VoiceContextPanel
        serverId={serverId}
        courseId={courseContext?.course.id}
        channelLabel={studyChannel?.name ?? courseContext?.channel.name ?? 'voice'}
      />
    )
  }

  return <ContextPlaceholder />
}

function QuestionsContextPanel({
  studyServerId,
  channelId,
}: {
  studyServerId: string | undefined
  channelId: string | undefined
}) {
  const userId = useAuthStore((state) => state.user?.id) ?? undefined
  const { selectedAnswer, studyServerId: panelServerId } = useQuestionsPanel()
  const resolvedServerId = studyServerId ?? panelServerId ?? undefined
  const navigationQuery = useStudyServerNavigationQuery(resolvedServerId)
  const canInstall = navigationQuery.data?.canViewFullCatalog ?? false
  const courseContext = channelId
    ? findCourseChannelContext(navigationQuery.data, channelId)
    : null
  const resourcesChannel = courseContext?.course.channels.find(
    (channel) => channel.name === 'resources',
  )
  const activeAnswer =
    selectedAnswer && channelId && selectedAnswer.channelId === channelId ? selectedAnswer : null

  const assistantQuery = useQuery({
    queryKey: studyAssistantPresenceQueryKey(resolvedServerId, userId),
    queryFn: () => fetchStudyAssistantPresence(resolvedServerId!, userId!),
    enabled: Boolean(resolvedServerId && userId),
  })

  const installFlow = useStudyAssistantInstallFlow({
    studyServerId: resolvedServerId,
    instructorUserId: userId,
  })

  return (
    <>
      <ContextPanelFrame eyebrow="AI context" title="Study Assistant">
        <ContextWidgetSection title="Install status">
            {assistantQuery.isLoading ? (
              <p className="mt-2 text-xs text-app-muted">Loading assistant grants…</p>
            ) : assistantQuery.isError ? (
              <p className="mt-2 text-xs text-rose-200">Could not load AI Study Assistant status.</p>
            ) : assistantQuery.data?.installed ? (
              <div className="mt-2 space-y-2 text-xs text-app-text">
                <p>Installed in this Study Server.</p>
                <p className="text-app-muted">
                  {assistantQuery.data.grants.length} grant
                  {assistantQuery.data.grants.length === 1 ? '' : 's'} active for channels and
                  resources.
                </p>
                <ul className="space-y-1 text-app-muted">
                  {assistantQuery.data.grants.slice(0, 4).map((grant) => (
                    <li key={`${grant.grantType}-${grant.grantTargetId}`}>
                      {grantLabel(grant.grantType)}
                    </li>
                  ))}
                </ul>
              </div>
            ) : canInstall ? (
              <div className="mt-2 space-y-3 text-xs text-app-text">
                <p className="text-app-muted">
                  Install the AI Study Assistant to ground answers in approved course materials.
                </p>
                {installFlow.installError && !installFlow.isDialogOpen ? (
                  <p role="alert" className="text-rose-200">
                    {installFlow.installError}
                  </p>
                ) : null}
                <Button
                  type="button"
                  className="px-3 py-1.5 text-xs"
                  disabled={installFlow.isOpening}
                  onClick={() => void installFlow.openInstallDialog()}
                >
                  {installFlow.isOpening ? 'Loading preview…' : 'Install AI Study Assistant'}
                </Button>
              </div>
            ) : (
              <p className="mt-2 text-xs text-app-muted">
                AI Study Assistant is not installed yet. Ask a Study Server owner or course instructor
                to install it from this panel.
              </p>
            )}
          </ContextWidgetSection>

          {courseContext ? (
            <>
              <CourseResourcesWidget
                serverId={resolvedServerId ?? ''}
                courseId={courseContext.course.id}
                courseTitle={courseContext.course.title}
                resourcesChannelId={resourcesChannel?.id ?? null}
                limit={4}
              />

              <ApprovedFaqsWidget
                serverId={resolvedServerId ?? ''}
                courseId={courseContext.course.id}
                limit={3}
              />
            </>
          ) : null}

          <ContextWidgetSection title="Grounding sources">
            {activeAnswer && activeAnswer.sources.length > 0 ? (
              <ul className="mt-2 space-y-2">
                {activeAnswer.sources.map((source) => (
                  <li
                    key={source.resourceId}
                    className="rounded-md border border-app-border bg-app-surface px-2 py-2"
                  >
                    <p className="text-sm font-medium text-app-text">{source.resourceTitle}</p>
                    <p className="mt-1 text-xs text-app-muted">{source.excerpt}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-2 text-xs text-app-muted">
                Ask AI on a support question to see grounded citation cards here.
              </p>
            )}
          </ContextWidgetSection>

          {activeAnswer?.handoffRecommended ? (
            <ContextWidgetSection title="Low confidence">
              <p className="text-xs text-amber-100">
                The assistant recommends human review. Use <strong>Add to TA Queue</strong> in the
                conversation when you want a teaching assistant to follow up.
              </p>
            </ContextWidgetSection>
          ) : null}
      </ContextPanelFrame>

      {installFlow.isDialogOpen && installFlow.preview ? (
        <StudyAssistantInstallDialog
          preview={installFlow.preview}
          selectedKeys={installFlow.selectedKeys}
          installError={installFlow.installError}
          isInstalling={installFlow.isInstalling}
          onToggleKey={installFlow.toggleGrantKey}
          onCancel={installFlow.closeDialog}
          onConfirm={installFlow.confirmInstall}
        />
      ) : null}
    </>
  )
}
