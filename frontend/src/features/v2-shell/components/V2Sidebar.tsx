import { useMemo, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  CalendarDays,
  ChevronDown,
  CreditCard,
  GraduationCap,
  Home as HomeIcon,
  Inbox,
  LogOut,
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
  v2CommunityPath,
} from '../v2-routes'
import type { V2SidebarData, V2SidebarServerGroup } from '../hooks/use-v2-sidebar-data'
import { useAuthStore } from '../../../stores/auth-store'
import { logout as logoutApi } from '../../auth/auth-api'

type V2SidebarProps = {
  data: V2SidebarData
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
  communityActive,
}: {
  group: V2SidebarServerGroup
  index: number
  collapsed: boolean
  onToggle: () => void
  activeServerId?: string
  activeCourseId?: string
  onNavigate: () => void
  communityActive: boolean
}) {
  return (
    <section className={`community${index > 0 ? ' second' : ''}`}>
      <div className={`community-header-row${communityActive ? ' active' : ''}`}><Link to={v2CommunityPath(group.id,'announcements')} onClick={onNavigate}>{communityIcon(index)}<span>{group.name}</span></Link><button type="button" onClick={onToggle} aria-label={`${collapsed ? 'Expand' : 'Collapse'} ${group.name}`}><ChevronDown size={18} className={collapsed ? '-rotate-90' : ''} style={{ transition: 'transform 220ms ease' }} /></button></div>
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
            </Link>
          ))}
        </div>
      ) : null}
    </section>
  )
}

export function V2Sidebar({ data, menuOpen, onCloseMenu }: V2SidebarProps) {
  const { serverId, courseId } = useParams()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const clearSession = useAuthStore((state) => state.clearSession)
  const displayName = user?.displayName?.split(' ')[0] ?? user?.email?.split('@')[0] ?? 'You'
  const [accountOpen, setAccountOpen] = useState(false)
  const [signingOut, setSigningOut] = useState(false)

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

  const signOut = async () => {
    setSigningOut(true)
    const refreshToken = useAuthStore.getState().refreshToken
    if (refreshToken) {
      try {
        await logoutApi(refreshToken)
      } catch {
        // A local sign-out must still complete if token revocation is unavailable.
      }
    }
    clearSession()
    onCloseMenu()
    navigate('/sign-in', { replace: true })
  }

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
            communityActive={pathname.includes(`/app/servers/${group.id}/community/`)}
          />
        ))}

        <Link to={v2JoinCreatePath()} className="join-create" onClick={onCloseMenu}>
          <Plus size={25} />
          <span>Join or create</span>
        </Link>
      </div>

      <div className="profile-menu-wrap">
        {accountOpen ? (
          <div className="account-menu" role="menu" aria-label="Account">
            <p>
              <strong>{user?.displayName ?? displayName}</strong>
              <small>{user?.email}</small>
            </p>
            {data.showBillingNav ? (
              <Link
                role="menuitem"
                to="/app/settings/billing"
                onClick={() => {
                  setAccountOpen(false)
                  onCloseMenu()
                }}
              >
                <CreditCard />
                Billing
              </Link>
            ) : null}
            <button
              role="menuitem"
              type="button"
              disabled={signingOut}
              onClick={() => void signOut()}
            >
              <LogOut />
              {signingOut ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        ) : null}
        <button
          type="button"
          className="profile"
          aria-label="Open account menu"
          aria-haspopup="menu"
          aria-expanded={accountOpen}
          onClick={() => setAccountOpen((current) => !current)}
        >
          <span className="avatar" aria-hidden="true">
            <span className="hair" />
            <span className="face">⌣</span>
            <i />
          </span>
          <strong>{displayName}</strong>
          <ChevronDown size={20} />
        </button>
      </div>
    </aside>
  )
}
