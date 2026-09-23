import { Navigate, useLocation } from 'react-router-dom'

/** Preserve existing bookmarks while using the complete Teaching interface. */
export function InstructorDashboardPage() {
  const { search } = useLocation()
  return <Navigate to={`/app/teaching${search}`} replace />
}
