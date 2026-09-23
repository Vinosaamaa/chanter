import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../lib/api-client'
import { useAuthStore } from '../../stores/auth-store'
import { getSourceDeletion, type SourceDeletionJob } from './source-deletion-api'
import './account-deletion.css'

const descriptions: Record<SourceDeletionJob['state'], [string, string]> = {
  ERASING: ['Cleanup in progress', 'Access is closed. The services are still processing this deletion request.'],
  WAITING_FOR_REPLICA: ['Recovery acknowledgement pending', 'Service cleanup results are recorded. Durable recovery records still need to acknowledge the deletion.'],
  COMPLETE: ['Deletion completed', 'All required cleanup results and recovery acknowledgements are recorded. Some recovery, accounting, shared-course and moderation records may remain.'],
}
const sourceNames: Record<string, string> = { auth: 'Account', community: 'Study Servers and memberships', message: 'Messages and questions', media: 'Course files', agent: 'Assistant history', search: 'Search', notification: 'Notifications' }

export function SourceDeletionPage() {
  const account = useAuthStore(state => state.user?.id)
  const generation = useAuthStore(state => state.generation)
  const { jobId = '' } = useParams()
  return account ? <SourceProgress key={`${account}:${generation}:${jobId}`} account={account} jobId={jobId} /> : null
}

function SourceProgress({ account, jobId }: { account: string; jobId: string }) {
  const query = useQuery({ queryKey: ['source-deletion', account, jobId], queryFn: ({ signal }) => getSourceDeletion(jobId, signal), retry: false, refetchOnWindowFocus: false })
  const job = !query.isError && query.data?.jobId === jobId ? query.data : undefined
  const copy = job ? descriptions[job.state] : undefined
  const unavailable = query.error instanceof ApiError && query.error.status === 404
  return <div className="deletion-page">
    <Link to="/app/picker">Back to your Study Servers</Link>
    <h1>{job?.targetKind === 'RESOURCE' ? 'Course file deletion' : job?.targetKind === 'STUDY_SERVER' ? 'Study Server deletion' : 'Deletion status'}</h1>
    <p>Keep this page address to return to the request. Only the account that requested deletion can read its progress.</p>
    {query.isPending ? <p role="status">Loading deletion status…</p> : null}
    {query.isError || (query.isSuccess && !job) ? <p role="alert">{unavailable
      ? 'Progress is not available yet. A new request may still be reaching the status service, or this account may not have access. Refresh to check again; this does not mean deletion completed.'
      : 'Could not verify deletion status. Refresh to check this same request; no new deletion has been submitted.'}</p> : null}
    {job && copy ? <section className="deletion-status" aria-label="Current deletion status" aria-live="polite">
      <h2>{copy[0]}</h2><p>{copy[1]}</p>
      {job.parts.some(part => part.errorCode) ? <p role="alert">A service could not finish. The request needs attention; a delivery failure does not mean deletion completed.</p> : null}
      <details><summary>Service results</summary><ul>{job.parts.map(part => <li key={part.source}>
        <span>{sourceNames[part.source] ?? 'Service'}</span>
        <span>{part.errorCode ? 'Delivery needs attention' : part.state === 'PRESERVED' ? 'Some records retained' : part.state === 'COMPLETE' ? 'Confirmed' : 'Pending'}</span>
      </li>)}</ul></details>
    </section> : null}
    <button type="button" disabled={query.isFetching} onClick={() => void query.refetch()}>Refresh status</button>
  </div>
}
