import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import { AuthSessionBootstrap } from './AuthSessionBootstrap'

import { ProtectedRoute } from '../features/auth/components/ProtectedRoute'

export function createAppRouter() {
  const routes = [
    {
      path: '/',
      lazy: async () => ({ Component: (await import('../features/marketing/pages/LandingPage')).LandingPage }),
    },
    {
      path: '/sign-in',
      lazy: async () => ({ Component: (await import('../features/auth/pages/SignInPage')).SignInPage }),
    },
    {
      path: '/forgot-password',
      lazy: async () => ({ Component: (await import('../features/auth/pages/ForgotPasswordPage')).ForgotPasswordPage }),
    },
    { path: '/appeal', lazy: async () => ({ Component: (await import('../features/moderation/AppealPage')).AppealPage }) },
    { path: '/account-deletion/:jobId', lazy: async () => ({ Component: (await import('../features/account-data/AccountDeletionPage')).AccountDeletionReceiptPage }) },
    { path: '/operator', element: <ProtectedRoute><Outlet /></ProtectedRoute>, children: [{ index: true, lazy: async () => ({ Component: (await import('../features/moderation/OperatorPage')).OperatorPage }) }] },
    {
      path: '/reset-password',
      lazy: async () => ({ Component: (await import('../features/auth/pages/ResetPasswordPage')).ResetPasswordPage }),
    },
    {
      path: '/verify-email',
      lazy: async () => ({ Component: (await import('../features/auth/pages/VerifyEmailPage')).VerifyEmailPage }),
    },
    {
      path: '/oauth/callback/google',
      lazy: async () => ({ Component: (await import('../features/auth/pages/OAuthCallbackPage')).OAuthCallbackPage }),
    },
    {
      path: '/terms',
      lazy: async () => ({ Component: (await import('../features/auth/pages/TermsPage')).TermsPage }),
    },
    {
      path: '/privacy',
      lazy: async () => ({ Component: (await import('../features/auth/pages/PrivacyPage')).PrivacyPage }),
    },
    {
      path: '/app',
      element: (
        <ProtectedRoute>
          <Outlet />
        </ProtectedRoute>
      ),
      children: [
        {
          lazy: async () => ({ Component: (await import('../features/v2-shell/layouts/V2AppShellLayout')).V2AppShellLayout }),
          children: [
            {
              index: true,
              element: <Navigate to="/app/home" replace />,
            },
            {
              path: 'home',
              lazy: async () => ({ Component: (await import('../features/v2-shell/pages/HomePage')).HomePage }),
            },
            {
              path: 'welcome',
              lazy: async () => ({ Component: (await import('../features/v2-shell/pages/WelcomeJoinedPage')).WelcomeJoinedPage }),
            },
            {
              path: 'onboarding/join-or-create',
              lazy: async () => ({ Component: (await import('../features/v2-shell/pages/onboarding/JoinOrCreatePage')).JoinOrCreatePage }),
            },
            {
              path: 'onboarding/create-study-server',
              lazy: async () => ({ Component: (await import('../features/v2-shell/pages/onboarding/CreateStudyServerV2Page')).CreateStudyServerV2Page }),
            },
            {
              path: 'inbox',
              lazy: async () => ({ Component: (await import('../features/v2-shell/pages/InboxPage')).InboxPage }),
            },
            {
              path: 'calendar',
              lazy: async () => ({ Component: (await import('../features/v2-shell/pages/CalendarPage')).CalendarPage }),
            },
            { path: 'teaching', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/TeachingPage')).TeachingPage }) },
            { path: 'instructor-dashboard', lazy: async () => ({ Component: (await import('../features/instructor-dashboard/components/InstructorDashboardPage')).InstructorDashboardPage }) },
            { path: 'settings/billing', element: <Navigate to="/app/settings/usage" replace /> },
            { path: 'settings/usage', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/UsageSettingsPage')).UsageSettingsPage }) },
            { path: 'account-data', lazy: async () => ({ Component: (await import('../features/account-data/AccountDataPage')).AccountDataPage }) },
            { path: 'account-data/delete', lazy: async () => ({ Component: (await import('../features/account-data/AccountDeletionPage')).AccountDeletionPage }) },
            { path: 'deletions/:jobId', lazy: async () => ({ Component: (await import('../features/account-data/SourceDeletionPage')).SourceDeletionPage }) },
            { path: 'friends', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/FriendsPage')).FriendsPage }) },
            { path: 'safety', lazy: async () => ({ Component: (await import('../features/moderation/SafetyPage')).SafetyPage }) },
            {
              path: 'servers/:serverId/courses/:courseId/settings',
              lazy: async () => ({ Component: (await import('../features/v2-shell/pages/course/CourseGovernancePage')).CourseGovernancePage }),
            },
            {
              path: 'servers/:serverId/courses/:courseId',
              lazy: async () => ({ Component: (await import('../features/v2-shell/layouts/V2CourseWorkspaceLayout')).V2CourseWorkspaceLayout }),
              children: [
                { index: true, element: <Navigate to="overview" replace /> },
                { path: 'overview', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/course/CourseOverviewPage')).CourseOverviewPage }) },
                { path: 'chat', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/course/CourseChatPage')).CourseChatPage }) },
                { path: 'questions', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/course/CourseQuestionsPage')).CourseQuestionsPage }) },
                { path: 'resources', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/course/CourseResourcesPage')).CourseResourcesPage }) },
                { path: 'office-hours', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/course/CourseOfficeHoursPage')).CourseOfficeHoursPage }) },
                { path: 'people', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/course/CoursePeoplePage')).CoursePeoplePage }) },
              ],
            },
            { path: 'servers/:serverId/community', lazy: async () => ({ Component: (await import('../features/v2-shell/layouts/V2CommunityHubLayout')).V2CommunityHubLayout }), children: [
              { index: true, element: <Navigate to="announcements" replace /> },
              { path: 'announcements', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/community/CommunityPages')).CommunityAnnouncementsPage }) },
              { path: 'lounge', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/community/CommunityPages')).CommunityLoungePage }) },
              { path: 'events', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/community/CommunityPages')).CommunityEventsPage }) },
              { path: 'discover', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/community/CommunityPages')).CommunityDiscoverPage }) },
              { path: 'members', lazy: async () => ({ Component: (await import('../features/v2-shell/pages/community/CommunityPages')).CommunityMembersPage }) },
            ] },
          ],
        },
        {
          lazy: async () => ({ Component: (await import('../features/shell/layouts/AppShellLayout')).AppShellLayout }),
          children: [
            {
              path: 'picker',
              lazy: async () => ({ Component: (await import('../features/shell/components/StudyServerPickerPage')).StudyServerPickerPage }),
            },
            {
              path: 'servers/:serverId/home',
              lazy: async () => ({ Component: (await import('../features/onboarding/components/StudyServerHomePage')).StudyServerHomePage }),
            },
            {
              path: 'servers/:serverId/courses/:courseId/enrollment',
              lazy: async () => ({ Component: (await import('../features/onboarding/components/CohortEnrollmentPage')).CohortEnrollmentPage }),
            },
            {
              path: 'servers/:serverId',
              lazy: async () => ({ Component: (await import('../features/shell/pages/AppServerRedirectPage')).AppServerRedirectPage }),
            },
            {
              path: 'servers/:serverId/study-channels/:channelId',
              lazy: async () => ({ Component: (await import('../features/shell/layouts/AppShellLayout')).AppChannelLayout }),
            },
            {
              path: 'servers/:serverId/course-channels/:channelId/summary',
              lazy: async () => ({ Component: (await import('../features/channel-summary/components/ChannelSummaryPage')).ChannelSummaryPage }),
            },
            {
              path: 'servers/:serverId/course-channels/:channelId',
              lazy: async () => ({ Component: (await import('../features/shell/layouts/AppShellLayout')).AppChannelLayout }),
            },
            {
              path: 'servers/:serverId/courses/:courseId/support/:operation',
              lazy: async () => ({ Component: (await import('../features/support-operations/components/SupportOperationPage')).SupportOperationPage }),
            },
          ],
        },
      ],
    },
    // DEV-only: Vite drops this branch (and the lazy import) from production builds (SEC-10).
    ...(import.meta.env.DEV
      ? [
          {
            path: '/dev/demo',
            lazy: async () => {
              const { DevDemoLazyRoute } = await import('../features/dev-demo/DevDemoLazyRoute')
              return { Component: DevDemoLazyRoute }
            },
          },
        ]
      : []),
    {
      path: '*',
      element: <Navigate to="/" replace />,
    },
  ]
  return createBrowserRouter([{
    element: <AuthSessionBootstrap><Outlet /></AuthSessionBootstrap>,
    children: routes,
  }])
}
