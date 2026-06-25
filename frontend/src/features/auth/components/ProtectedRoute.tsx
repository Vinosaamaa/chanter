import { Navigate, useLocation } from 'react-router-dom'

import { useAuthStore } from '../../../stores/auth-store'

type ProtectedRouteProps = {
  children: React.ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const location = useLocation()

  if (!accessToken) {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />
  }

  return children
}
