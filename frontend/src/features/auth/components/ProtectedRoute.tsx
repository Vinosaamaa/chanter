import { Navigate, useLocation } from 'react-router-dom'
import { useState } from 'react'

import { useAuthStore } from '../../../stores/auth-store'

type ProtectedRouteProps = {
  children: React.ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const location = useLocation()
  const [startedAuthenticated] = useState(() => Boolean(accessToken))

  if (!accessToken) {
    return (
      <Navigate
        to="/sign-in"
        replace
        state={startedAuthenticated ? undefined : { from: location.pathname }}
      />
    )
  }

  return children
}
