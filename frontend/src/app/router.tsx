import { createBrowserRouter, Navigate } from 'react-router-dom'

import { ProtectedRoute } from '../features/auth/components/ProtectedRoute'
import { SignInPage } from '../features/auth/pages/SignInPage'
import DevDemoApp from '../features/dev-demo/DevDemoApp'
import { DevDemoRoutePage } from '../features/dev-demo/DevDemoRoutePage'
import { LandingPage } from '../features/marketing/pages/LandingPage'
import { AppChannelLayout, AppShellLayout } from '../features/shell/layouts/AppShellLayout'
import { AppHomeRedirectPage, AppServerRedirectPage } from '../features/shell/pages/AppServerRedirectPage'

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
          <AppShellLayout />
        </ProtectedRoute>
      ),
      children: [
        {
          index: true,
          element: <AppHomeRedirectPage />,
        },
        {
          path: 'friends',
          element: (
            <p className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
              Friends hub lands in #31 (Workable Product).
            </p>
          ),
        },
        {
          path: 'instructor-dashboard',
          element: (
            <p className="flex flex-1 items-center justify-center p-6 text-sm text-app-muted">
              Instructor dashboard lands in #55.
            </p>
          ),
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
          path: 'servers/:serverId/course-channels/:channelId',
          element: <AppChannelLayout />,
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
