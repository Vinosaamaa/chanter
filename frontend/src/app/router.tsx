import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'

import { ProtectedRoute } from '../features/auth/components/ProtectedRoute'
import { SignInPage } from '../features/auth/pages/SignInPage'
import DevDemoApp from '../features/dev-demo/DevDemoApp'
import { DevDemoRoutePage } from '../features/dev-demo/DevDemoRoutePage'
import { LandingPage } from '../features/marketing/pages/LandingPage'
import { StudyServerPickerPage } from '../features/shell/components/StudyServerPickerPage'
import { AppChannelLayout, AppShellLayout } from '../features/shell/layouts/AppShellLayout'
import { AppServerRedirectPage } from '../features/shell/pages/AppServerRedirectPage'
import { SupportOperationPage } from '../features/support-operations/components/SupportOperationPage'
import { InstructorDashboardPage } from '../features/instructor-dashboard/components/InstructorDashboardPage'
import { ChannelSummaryPage } from '../features/channel-summary/components/ChannelSummaryPage'
import { CohortEnrollmentPage } from '../features/onboarding/components/CohortEnrollmentPage'
import { StudyServerHomePage } from '../features/onboarding/components/StudyServerHomePage'
import { V2AppShellLayout } from '../features/v2-shell/layouts/V2AppShellLayout'
import { HomePage } from '../features/v2-shell/pages/HomePage'
import { WelcomeJoinedPage } from '../features/v2-shell/pages/WelcomeJoinedPage'
import { CreateStudyServerV2Page } from '../features/v2-shell/pages/onboarding/CreateStudyServerV2Page'
import { InboxPage } from '../features/v2-shell/pages/InboxPage'
import { CalendarPage } from '../features/v2-shell/pages/CalendarPage'
import { V2CourseWorkspaceLayout } from '../features/v2-shell/layouts/V2CourseWorkspaceLayout'
import { CourseOverviewPage } from '../features/v2-shell/pages/course/CourseOverviewPage'
import { CourseChatPage } from '../features/v2-shell/pages/course/CourseChatPage'
import { CourseQuestionsPage } from '../features/v2-shell/pages/course/CourseQuestionsPage'
import { CourseResourcesPage } from '../features/v2-shell/pages/course/CourseResourcesPage'
import { CourseOfficeHoursPage } from '../features/v2-shell/pages/course/CourseOfficeHoursPage'
import { CoursePeoplePage } from '../features/v2-shell/pages/course/CoursePeoplePage'
import { V2CommunityHubLayout } from '../features/v2-shell/layouts/V2CommunityHubLayout'
import { CommunityAnnouncementsPage, CommunityDiscoverPage, CommunityEventsPage, CommunityLoungePage, CommunityMembersPage } from '../features/v2-shell/pages/community/CommunityPages'

export function createAppRouter() {
  return createBrowserRouter([
    {
      path: '/',
      element: <LandingPage />,
    },
    {
      path: '/sign-in',
      element: <SignInPage />,
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
          element: <V2AppShellLayout />,
          children: [
            {
              index: true,
              element: <Navigate to="/app/home" replace />,
            },
            {
              path: 'home',
              element: <HomePage />,
            },
            {
              path: 'welcome',
              element: <WelcomeJoinedPage />,
            },
            {
              path: 'onboarding/create-study-server',
              element: <CreateStudyServerV2Page />,
            },
            {
              path: 'inbox',
              element: <InboxPage />,
            },
            {
              path: 'calendar',
              element: <CalendarPage />,
            },
            {
              path: 'servers/:serverId/courses/:courseId',
              element: <V2CourseWorkspaceLayout />,
              children: [
                { index: true, element: <Navigate to="overview" replace /> },
                { path: 'overview', element: <CourseOverviewPage /> },
                { path: 'chat', element: <CourseChatPage /> },
                { path: 'questions', element: <CourseQuestionsPage /> },
                { path: 'resources', element: <CourseResourcesPage /> },
                { path: 'office-hours', element: <CourseOfficeHoursPage /> },
                { path: 'people', element: <CoursePeoplePage /> },
              ],
            },
            { path: 'servers/:serverId/community', element: <V2CommunityHubLayout />, children: [
              { index: true, element: <Navigate to="announcements" replace /> },
              { path: 'announcements', element: <CommunityAnnouncementsPage /> },
              { path: 'lounge', element: <CommunityLoungePage /> },
              { path: 'events', element: <CommunityEventsPage /> },
              { path: 'discover', element: <CommunityDiscoverPage /> },
              { path: 'members', element: <CommunityMembersPage /> },
            ] },
          ],
        },
        {
          element: <AppShellLayout />,
          children: [
            {
              path: 'picker',
              element: <StudyServerPickerPage />,
            },
            {
              path: 'instructor-dashboard',
              element: <InstructorDashboardPage />,
            },
            {
              path: 'servers/:serverId/home',
              element: <StudyServerHomePage />,
            },
            {
              path: 'servers/:serverId/courses/:courseId/enrollment',
              element: <CohortEnrollmentPage />,
            },
            {
              path: 'servers/:serverId',
              element: <AppServerRedirectPage />,
            },
            {
              path: 'servers/:serverId/study-channels/:channelId',
              element: <AppChannelLayout />,
            },
            {
              path: 'servers/:serverId/course-channels/:channelId/summary',
              element: <ChannelSummaryPage />,
            },
            {
              path: 'servers/:serverId/course-channels/:channelId',
              element: <AppChannelLayout />,
            },
            {
              path: 'servers/:serverId/courses/:courseId/support/:operation',
              element: <SupportOperationPage />,
            },
          ],
        },
      ],
    },
    {
      path: '/dev/demo',
      element: (
        <>
          <DevDemoRoutePage />
          <DevDemoApp />
        </>
      ),
    },
    {
      path: '*',
      element: <Navigate to="/" replace />,
    },
  ])
}
