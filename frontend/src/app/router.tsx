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
import { CreateStudyServerPage } from '../features/onboarding/components/CreateStudyServerPage'
import { CohortEnrollmentPage } from '../features/onboarding/components/CohortEnrollmentPage'
import { StudyServerHomePage } from '../features/onboarding/components/StudyServerHomePage'
import { V2AppShellLayout } from '../features/v2-shell/layouts/V2AppShellLayout'
import { HomePage } from '../features/v2-shell/pages/HomePage'

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
