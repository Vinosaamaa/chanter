import { useOutletContext } from 'react-router-dom'

import type { StudyServerCapabilities, StudyServerNavigation } from '../../shell/types'

export type V2CommunityContext = {
  serverId: string
  serverName: string
  isOwner: boolean
  studyServerCapabilities: StudyServerCapabilities | undefined
  navigation: StudyServerNavigation | undefined
}

export function useV2Community(): V2CommunityContext {
  return useOutletContext<V2CommunityContext>()
}
