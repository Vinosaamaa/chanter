import { createBrowserRouter, Navigate } from 'react-router-dom'

import { SignInPage } from '../features/auth/pages/SignInPage'
import DevDemoApp from '../features/dev-demo/DevDemoApp'
import { DevDemoRoutePage } from '../features/dev-demo/DevDemoRoutePage'
import { LandingPage } from '../features/marketing/pages/LandingPage'
import { AppShellLayout } from '../features/shell/layouts/AppShellLayout'
import { AppShellPlaceholderPage } from '../features/shell/pages/AppShellPlaceholderPage'

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
      element: <AppShellLayout />,
      children: [
        {
          index: true,
          element: <AppShellPlaceholderPage />,
        },
        {
          path: 'courses',
          element: (
            <p className="text-sm text-app-muted">My courses navigation lands in #50.</p>
          ),
        },
        {
          path: 'friends',
          element: <p className="text-sm text-app-muted">Friends hub lands in #31 (Workable Product).</p>,
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
