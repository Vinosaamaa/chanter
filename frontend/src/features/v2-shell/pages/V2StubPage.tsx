type V2StubPageProps = {
  title: string
  description?: string
}

export function V2StubPage({ title, description }: V2StubPageProps) {
  return (
    <div className="flex min-h-0 flex-1 items-center justify-center bg-v2-bg px-6">
      <div className="max-w-md rounded-xl border border-v2-border bg-v2-card p-8 text-center">
        <h1 className="text-xl font-semibold text-v2-text">{title}</h1>
        <p className="mt-2 text-sm text-v2-muted">
          {description ?? 'This screen ships in a follow-up UI v2 slice.'}
        </p>
      </div>
    </div>
  )
}
