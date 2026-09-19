import { Link } from 'react-router-dom'
import type { SourceType } from './moderation-api'

export function ReportLink({ type, id, label = 'Report' }: { type: SourceType; id: string; label?: string }) {
  return <Link className="text-sm text-app-muted underline" to={`/app/safety?${new URLSearchParams({ type, id })}`}>{label}</Link>
}
