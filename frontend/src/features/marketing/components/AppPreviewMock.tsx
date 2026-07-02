export function AppPreviewMock() {
  return (
    <div
      aria-hidden
      className="relative overflow-hidden rounded-2xl border border-app-border bg-app-surface shadow-2xl shadow-black/40"
    >
      <div className="flex border-b border-app-border bg-app-elevated px-3 py-2">
        <div className="flex gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-rose-400/80" />
          <span className="h-2.5 w-2.5 rounded-full bg-amber-400/80" />
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-400/80" />
        </div>
        <p className="mx-auto text-[10px] text-app-muted">CS 101 · Study Server preview</p>
      </div>

      <div className="grid min-h-[320px] grid-cols-[88px_minmax(0,1fr)_120px] text-[10px] md:min-h-[380px] md:grid-cols-[100px_minmax(0,1fr)_140px]">
        <aside className="border-r border-app-border bg-[#1a1b1e] p-2">
          <p className="mb-2 truncate font-semibold text-app-text">CS 101</p>
          <p className="mb-1 text-[9px] uppercase tracking-wide text-app-muted">Course</p>
          <ul className="space-y-0.5 text-app-muted">
            <li className="rounded bg-app-accent/20 px-1 py-0.5 text-app-text"># announcements</li>
            <li className="px-1"># resources</li>
          </ul>
          <p className="mb-1 mt-2 text-[9px] uppercase tracking-wide text-app-muted">Support</p>
          <ul className="space-y-0.5 text-app-muted">
            <li className="px-1">TA Queue</li>
            <li className="px-1">AI Assistant</li>
          </ul>
        </aside>

        <section className="flex flex-col bg-app-bg p-3">
          <p className="mb-2 font-medium text-app-text"># announcements</p>
          <div className="space-y-2">
            <div className="rounded-md border border-app-border/60 bg-app-surface/60 p-2">
              <p className="text-[9px] text-app-accent">Instructor</p>
              <p className="mt-0.5 text-app-muted">Welcome to CS 101! Check #resources for the syllabus.</p>
            </div>
            <div className="rounded-md border border-indigo-500/30 bg-indigo-500/10 p-2">
              <p className="text-[9px] text-indigo-300">AI Study Assistant</p>
              <p className="mt-0.5 text-app-muted">Ask me anything about this week&apos;s lecture.</p>
            </div>
          </div>
        </section>

        <aside className="border-l border-app-border bg-app-surface p-2">
          <p className="mb-2 font-medium text-app-text">TA Queue</p>
          <ul className="space-y-1.5 text-app-muted">
            <li className="rounded border border-app-border/60 p-1.5">
              <p className="text-app-text">Alex R.</p>
              <p>Recursion help · 5m</p>
            </li>
            <li className="rounded border border-app-border/60 p-1.5">
              <p className="text-app-text">Jamie T.</p>
              <p>Big O · 12m</p>
            </li>
          </ul>
        </aside>
      </div>
    </div>
  )
}
