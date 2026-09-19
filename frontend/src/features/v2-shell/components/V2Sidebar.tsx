import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useParams } from 'react-router-dom'
import {
  CalendarDays,
  ChevronDown,
  ChartNoAxesColumn,
  GraduationCap,
  Home as HomeIcon,
  Inbox,
  LogOut,
  Plus,
  Sprout,
  ShieldCheck,
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
import { useUnreadNotificationCountQuery } from '../../inbox/hooks/use-inbox-queries'
import { useAuthStore } from '../../../stores/auth-store'
import { useSignOut } from '../../auth/hooks/use-sign-out'
import { SessionsDialog } from '../../auth/components/SessionsDialog'

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
  const user = useAuthStore((state) => state.user)
  const endSession = useSignOut()
  const displayName = user?.displayName?.split(' ')[0] ?? user?.email?.split('@')[0] ?? 'You'
  const [accountOpen, setAccountOpen] = useState(false)
  const [signingOut, setSigningOut] = useState(false)
  const [sessionsOpen, setSessionsOpen] = useState(false)
  const profileRef = useRef<HTMLButtonElement>(null)
  const unreadQuery = useUnreadNotificationCountQuery()
  const unreadCount = unreadQuery.data?.unreadCount ?? 0
  const sidebarRef = useRef<HTMLElement>(null)

  useEffect(() => {
    if (!menuOpen) return
    const focusFrame = requestAnimationFrame(() => sidebarRef.current?.querySelector<HTMLButtonElement>('.sidebar-close')?.focus())
    const handleKey = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLElement && event.target.closest('dialog[open]')) return
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseMenu()
      }
      if (event.key !== 'Tab') return
      const controls = [...(sidebarRef.current?.querySelectorAll<HTMLElement>('a[href], button:not(:disabled), input:not(:disabled), [tabindex="0"]') ?? [])]
      const first = controls[0]
      const last = controls.at(-1)
      if (event.shiftKey && (document.activeElement === first || !sidebarRef.current?.contains(document.activeElement))) {
        event.preventDefault()
        last?.focus()
      } else if (!event.shiftKey && (document.activeElement === last || !sidebarRef.current?.contains(document.activeElement))) {
        event.preventDefault()
        first?.focus()
      }
    }
    document.addEventListener('keydown', handleKey)
    return () => { cancelAnimationFrame(focusFrame); document.removeEventListener('keydown', handleKey) }
  }, [menuOpen, onCloseMenu])

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
    await endSession()
    onCloseMenu()
  }

  return (
    <>
    <aside ref={sidebarRef} id="course-navigation" className={`sidebar${menuOpen ? ' open' : ''}`} role={menuOpen ? 'dialog' : undefined} aria-modal={menuOpen || undefined} aria-label={menuOpen ? 'Browse Chanter' : undefined}>

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
            {unreadCount > 0 ? <b>{unreadCount > 99 ? '99+' : unreadCount}</b> : null}
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
        <p className="sidebar-section-title">Study Servers</p>

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
            <button role="menuitem" type="button" onClick={() => {
              setAccountOpen(false)
              setSessionsOpen(true)
            }}><ShieldCheck />Sessions and devices</button>
            {data.showBillingNav ? (
              <Link
                role="menuitem"
                to="/app/settings/usage"
                onClick={() => {
                  setAccountOpen(false)
                  onCloseMenu()
                }}
              >
                <ChartNoAxesColumn />
                Usage
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
          ref={profileRef}
          aria-label="Open account menu"
          aria-haspopup="menu"
          aria-expanded={accountOpen}
          onClick={() => setAccountOpen((current) => !current)}
        >
          <span className="account-initial" aria-hidden="true">{displayName.slice(0, 1).toUpperCase()}</span>
          <strong>{displayName}</strong>
          <ChevronDown size={20} />
        </button>
      </div>
    </aside>
    {sessionsOpen ? <SessionsDialog onClose={() => {
      setSessionsOpen(false)
      profileRef.current?.focus()
    }} /> : null}
    </>
  )
}
