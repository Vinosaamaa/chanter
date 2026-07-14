import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet, useParams } from 'react-router-dom'
import { Sprout, UserPlus } from 'lucide-react'

import { courseCatalogQueryKey, fetchCourseCatalog } from '../../course-discovery/course-discovery-api'
import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { V2Avatar } from '../components/V2Avatar'
import { v2CommunityPath, type V2CommunityTab } from '../v2-routes'
import type { V2CommunityContext } from './v2-community-context'

const tabs: { id: V2CommunityTab; label: string }[] = [
  { id: 'announcements', label: 'Announcements' }, { id: 'lounge', label: 'Lounge' }, { id: 'events', label: 'Events' }, { id: 'discover', label: 'Discover courses' }, { id: 'members', label: 'Members' },
]

export function V2CommunityHubLayout() {
  const { serverId = 'server-demo' } = useParams()
  const navigationQuery = useStudyServerNavigationQuery(serverId)
  const catalogQuery = useQuery({
    queryKey: courseCatalogQueryKey(serverId, '', 'ALL'),
    queryFn: () => fetchCourseCatalog(serverId, { search: '', filter: 'ALL' }),
    enabled: serverId !== 'server-demo',
  })
  const serverName = navigationQuery.data?.studyServerName ?? 'Spring Bootcamp Hub'
  const courseCount = catalogQuery.data?.courses.length ?? navigationQuery.data?.courses.length ?? 0
  const context: V2CommunityContext = {
    serverId,
    serverName,
    isOwner: navigationQuery.data?.capabilities.owner ?? false,
    studyServerCapabilities: navigationQuery.data?.capabilities,
    navigation: navigationQuery.data,
  }
  return <section className="v2-workspace-page community-hub-page"><header className="community-hub-chrome"><div className="community-hub-title"><span><Sprout /></span><div><h1>{serverName}</h1><p>Community · 214 members · {courseCount} courses</p>{context.studyServerCapabilities?.canManageCommunity ? <button type="button" className="v2-primary-button"><UserPlus />Invite people</button> : null}</div><div className="community-member-stack">{['Sam','Priya','Jordan','Maria'].map((name,index) => <V2Avatar key={name} name={name} tone={index % 2 ? 'purple' : 'amber'} size="lg" />)}<i>+11</i></div></div><nav className="community-tabs">{tabs.map((tab) => <NavLink key={tab.id} to={v2CommunityPath(serverId,tab.id)}>{tab.label}</NavLink>)}</nav></header><div className="community-tab-panel"><Outlet context={context} /></div></section>
}
