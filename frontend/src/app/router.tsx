import { createBrowserRouter, Navigate } from 'react-router-dom'

import { ProtectedRoute } from '../features/auth/components/ProtectedRoute'
import { SignInPage } from '../features/auth/pages/SignInPage'
import DevDemoApp from '../features/dev-demo/DevDemoApp'
import { DevDemoRoutePage } from '../features/dev-demo/DevDemoRoutePage'
import { FriendsHubPage } from '../features/friends/components/FriendsHubPage'
import { FriendsHubLayout } from '../features/friends/layouts/FriendsHubLayout'
import { LandingPage } from '../features/marketing/pages/LandingPage'
import { AppChannelLayout, AppShellLayout } from '../features/shell/layouts/AppShellLayout'
import { AppHomeRedirectPage, AppServerRedirectPage } from '../features/shell/pages/AppServerRedirectPage'
import { SupportOperationPage } from '../features/support-operations/components/SupportOperationPage'
import { InstructorDashboardPage } from '../features/instructor-dashboard/components/InstructorDashboardPage'
import { ChannelSummaryPage } from '../features/channel-summary/components/ChannelSummaryPage'
import { CreateStudyServerPage } from '../features/onboarding/components/CreateStudyServerPage'
import { CohortEnrollmentPage } from '../features/onboarding/components/CohortEnrollmentPage'
import { StudyServerHomePage } from '../features/onboarding/components/StudyServerHomePage'

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
      path: '/app/onboarding/create-study-server',
      element: (
        <ProtectedRoute>
          <CreateStudyServerPage />
        </ProtectedRoute>
      ),
    },
    {
      path: '/app',
      element: (
        <ProtectedRoute>
          <AppShellLayout />
        </ProtectedRoute>
      ),
      children: [
        {
          index: true,
          element: <AppHomeRedirectPage />,
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
    {
      path: '/app/friends',
      element: (
        <ProtectedRoute>
          <FriendsHubLayout />
        </ProtectedRoute>
      ),
      children: [
        {
          index: true,
          element: <FriendsHubPage />,
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
