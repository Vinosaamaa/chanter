/** Explicit local visual-review server. Never used by build or product startup. */
import { defineConfig, mergeConfig } from 'vite'
import base from './vite.config'

const user = { id: 'visual-learner', email: 'learner@example.test', displayName: 'Sam Rivera', emailVerified: true }
const titles = ['CS 101 — Foundations of computer science', 'Designing for people', 'Mathematics for everyday systems', 'Writing with clarity']
const coursePath = (id: string) => `/app/servers/visual-study/courses/${id}/overview?cohort=visual-cohort`
const courseCapabilities = { instructor: false, teachingAssistant: false, enrolled: true, canManageCourse: false, canManageQuestions: false, canApproveFaq: false, canManageTaQueue: false, canUploadResources: false, canScheduleOfficeHours: false, canManagePeople: false }
const courses = titles.map((title, index) => ({ id: `visual-course-${index}`, title, capabilities: courseCapabilities, cohorts: [{ id: 'visual-cohort', name: 'Autumn cohort', capabilities: { enrolled: true, teachingAssistant: false, canManage: false } }], channels: [] }))

export default mergeConfig(base, defineConfig({
  server: { host: '127.0.0.1', port: 4174, strictPort: true, proxy: {} },
  plugins: [{
    name: 'explicit-visual-review-fixtures',
    transformIndexHtml() {
      return [
        { tag: 'script', attrs: { type: 'module' }, injectTo: 'head-prepend', children: `import { useAuthStore } from '/src/stores/auth-store.ts'; document.cookie = 'visual_review=' + (new URLSearchParams(location.search).get('visual') === 'empty' ? 'empty' : 'populated') + '; Path=/; SameSite=Strict'; useAuthStore.getState().setSession(${JSON.stringify({ accessToken: 'visual-fixture-only', expiresInSeconds: 900, user })});` },
        { tag: 'div', attrs: { style: 'position:fixed;right:12px;bottom:80px;z-index:90;background:#192c46;color:white;padding:4px 8px;border-radius:4px;font:10px system-ui;pointer-events:none' }, injectTo: 'body', children: 'UI fixture preview' },
      ]
    },
    configureServer(server) {
      server.middlewares.use(async (request, response, next) => {
        const pathname = new URL(request.url ?? '/', 'http://localhost').pathname
        if (!pathname.startsWith('/api/')) return next()
        response.setHeader('Content-Type', 'application/json')
        const session = { accessToken: 'visual-fixture-only', expiresInSeconds: 900, user }
        const send = (body: unknown) => response.end(JSON.stringify(body))
        const empty = request.headers.cookie?.includes('visual_review=empty')
        if (pathname.endsWith('/oauth/providers')) return send({ providers: [] })
        if (pathname.endsWith('/auth/login')) { response.setHeader('Set-Cookie', 'visual_session=1; Path=/; SameSite=Strict'); return send(session) }
        if (pathname.endsWith('/auth/refresh')) return send(session)
        if (pathname.endsWith('/auth/logout')) { response.setHeader('Set-Cookie', 'visual_session=; Path=/; Max-Age=0'); response.statusCode = 204; return response.end() }
        if (pathname.endsWith('/auth/me')) return send(user)
        if (pathname.endsWith('/study-servers')) return send(empty ? [] : [{ id: 'visual-study', name: 'Open Learning Collective', owner: false, courseCount: 4, memberCount: 28 }])
        if (pathname.endsWith('/navigation')) return send({ studyServerId: 'visual-study', studyServerName: 'Open Learning Collective', canViewFullCatalog: false, capabilities: { owner: false, canTeach: false, canCreateCourse: false, canManageCommunity: false, canManageEvents: false, canManageBilling: false }, studyServerChannels: [], courses })
        if (pathname.endsWith('/home-summary')) return send(empty ? { courses: [], attention: [], upNext: [], partialFailures: [] } : {
          courses: courses.map((course, index) => ({ courseId: course.id, studyServerId: 'visual-study', title: course.title, cohortId: 'visual-cohort', cohortName: 'Autumn cohort', instructorDisplayName: ['Dr. Ada Chen', 'Jordan Park', 'Maya Thompson', 'Alex Morgan'][index], progress: [38, 62, null, 15][index], href: coursePath(course.id) })),
          attention: [{ id: 'visual-update', kind: 'ANNOUNCEMENTS', headline: 'A new note from your instructor', suffix: 'Course materials for this week are ready.', actionLabel: 'Read announcement', href: '/app/servers/visual-study/community/announcements' }],
          upNext: [{ id: 'visual-event', kind: 'OFFICE_HOURS', title: 'Office Hours', suffix: 'Foundations of computer science', detail: 'Tomorrow, 2:00 PM', actionLabel: 'View session', href: '/app/servers/visual-study/courses/visual-course-0/office-hours' }, { id: 'visual-event-2', kind: 'EVENT', title: 'Community study circle', suffix: 'Open Learning Collective', detail: 'Monday, 4:30 PM', actionLabel: 'View event', href: '/app/servers/visual-study/community/events' }], partialFailures: [],
        })
        if (pathname.endsWith('/unread-count')) return send({ unreadCount: 2 })
        if (pathname.endsWith('/study-server-invitations')) return send([])
        response.statusCode = 404
        return send({ message: `Visual fixture not implemented for ${pathname}` })
      })
    },
  }],
}))
