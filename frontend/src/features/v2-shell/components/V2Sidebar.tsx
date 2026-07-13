import { useMemo, useState } from 'react'
import { Link, NavLink, useParams } from 'react-router-dom'
import {
  CalendarDays,
  ChevronDown,
  GraduationCap,
  Home as HomeIcon,
  Inbox,
  Plus,
  Sprout,
  UsersRound,
  X,
} from 'lucide-react'

import {
  v2CalendarPath,
  v2CoursePath,
  v2FriendsPath,
  v2HomePath,
  v2InboxPath,
  v2JoinCreatePath,
  v2TeachingPath,
} from '../v2-routes'
import type { V2SidebarData, V2SidebarServerGroup } from '../hooks/use-v2-sidebar-data'
import { useAuthStore } from '../../../stores/auth-store'

type V2SidebarProps = {
  data: V2SidebarData
  inboxUnread?: number
  menuOpen: boolean
  onCloseMenu: () => void
}

function communityIcon(index: number) {
  return index % 2 === 0 ? (
    <span className="community-icon green">
      <Sprout size={20} fill="currentColor" />
    </span>
  ) : (
    <span className="community-icon blue">
      <UsersRound size={20} fill="currentColor" />
    </span>
  )
}

function ServerGroupSection({
  group,
  index,
  collapsed,
  onToggle,
  activeServerId,
  activeCourseId,
  onNavigate,
}: {
  group: V2SidebarServerGroup
  index: number
  collapsed: boolean
  onToggle: () => void
  activeServerId?: string
  activeCourseId?: string
  onNavigate: () => void
}) {
  return (
    <section className={`community${index > 0 ? ' second' : ''}`}>
      <button type="button" className="community-header" onClick={onToggle}>
        {communityIcon(index)}
        <span>{group.name}</span>
        <ChevronDown size={18} className={collapsed ? '-rotate-90' : ''} style={{ transition: 'transform 220ms ease' }} />
      </button>
      {!collapsed ? (
        <div className={`community-list${group.courses.length === 1 ? ' one-item' : ''}`}>
          {group.courses.map((course) => (
            <Link
              key={course.id}
              to={v2CoursePath(course.serverId, course.id, 'overview')}
              onClick={onNavigate}
              className={
                activeServerId === course.serverId && activeCourseId === course.id ? 'active' : undefined
              }
            >
              <i style={{ background: course.accentColor }} />
              <span>{course.title}</span>
              {course.unreadCount > 0 ? <b>{course.unreadCount}</b> : null}
            </Link>
          ))}
        </div>
      ) : null}
    </section>
  )
}

export function V2Sidebar({ data, inboxUnread = 4, menuOpen, onCloseMenu }: V2SidebarProps) {
  const { serverId, courseId } = useParams()
  const user = useAuthStore((state) => state.user)
  const displayName = user?.displayName?.split(' ')[0] ?? user?.email?.split('@')[0] ?? 'You'

  const initialCollapsed = useMemo(() => {
    const ids = new Set<string>()
    for (const group of data.serverGroups) {
      if (!group.expanded) {
        ids.add(group.id)
      }
    }
    return ids
  }, [data.serverGroups])

  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(initialCollapsed)

  const toggleGroup = (groupId: string) => {
    setCollapsedGroups((current) => {
      const next = new Set(current)
      if (next.has(groupId)) {
        next.delete(groupId)
      } else {
        next.add(groupId)
      }
      return next
    })
  }

  const navClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'active' : undefined)

  return (
    <aside className={`sidebar${menuOpen ? ' open' : ''}`}>
      <div className="sidebar-scroll">
        <div className="brand">
          <span className="brand-mark">
            <i />
            <i />
          </span>
          <strong>Chanter</strong>
          <button
            type="button"
            className="sidebar-close"
            aria-label="Close navigation"
            onClick={onCloseMenu}
          >
            <X size={23} />
          </button>
        </div>

        <nav className="main-nav" aria-label="Primary navigation">
          <NavLink to={v2HomePath()} className={navClass} onClick={onCloseMenu}>
            <HomeIcon />
            <span>Home</span>
          </NavLink>
          {data.showTeachingNav ? (
            <NavLink to={v2TeachingPath()} className={navClass} onClick={onCloseMenu}>
              <GraduationCap />
              <span>Teaching</span>
            </NavLink>
          ) : null}
          <NavLink to={v2InboxPath()} className={navClass} onClick={onCloseMenu}>
            <Inbox />
            <span>Inbox</span>
            {inboxUnread > 0 ? <b>{inboxUnread}</b> : null}
          </NavLink>
          <NavLink to={v2CalendarPath()} className={navClass} onClick={onCloseMenu}>
            <CalendarDays />
            <span>Calendar</span>
          </NavLink>
          <NavLink to={v2FriendsPath()} className={navClass} onClick={onCloseMenu}>
            <UsersRound />
            <span>Friends</span>
          </NavLink>
        </nav>

        <div className="sidebar-rule" />

        {data.isLoading ? <p style={{ padding: '0 8px', color: 'var(--muted)' }}>Loading courses…</p> : null}
        {data.isError ? <p style={{ padding: '0 8px', color: '#fca5a5' }}>Could not load courses.</p> : null}

        {data.serverGroups.map((group, index) => (
          <ServerGroupSection
            key={group.id}
            group={group}
            index={index}
            collapsed={collapsedGroups.has(group.id)}
            onToggle={() => toggleGroup(group.id)}
            activeServerId={serverId}
            activeCourseId={courseId}
            onNavigate={onCloseMenu}
          />
        ))}

        <Link to={v2JoinCreatePath()} className="join-create" onClick={onCloseMenu}>
          <Plus size={25} />
          <span>Join or create</span>
        </Link>
      </div>

      <button type="button" className="profile">
        <span className="avatar" aria-hidden="true">
          <span className="hair" />
          <span className="face">⌣</span>
          <i />
        </span>
        <strong>{displayName}</strong>
        <ChevronDown size={20} />
      </button>
    </aside>
  )
}
