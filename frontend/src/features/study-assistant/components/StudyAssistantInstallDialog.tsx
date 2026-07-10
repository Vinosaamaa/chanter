import { useEffect, useRef } from 'react'

import { Button } from '../../../components/ui/button'
import {
  allGrantKeysFromPreview,
  grantKey,
} from '../study-assistant-grants'
import type { StudyAssistantInstallPreview } from '../study-assistant-types'

function focusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(
    container.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  )
}

type StudyAssistantInstallDialogProps = {
  preview: StudyAssistantInstallPreview
  selectedKeys: Set<string>
  installError: string | null
  isInstalling: boolean
  onToggleKey: (key: string, checked: boolean) => void
  onCancel: () => void
  onConfirm: () => void
}

export function StudyAssistantInstallDialog({
  preview,
  selectedKeys,
  installError,
  isInstalling,
  onToggleKey,
  onCancel,
  onConfirm,
}: StudyAssistantInstallDialogProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const previouslyFocusedRef = useRef<HTMLElement | null>(null)
  const alreadyInstalled = preview.alreadyInstalled
  const canConfirm = !alreadyInstalled && selectedKeys.size > 0 && !isInstalling

  useEffect(() => {
    previouslyFocusedRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null

    const focusable = panelRef.current ? focusableElements(panelRef.current) : []
    focusable[0]?.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCancel()
        return
      }

      if (event.key !== 'Tab' || !panelRef.current) {
        return
      }

      const trapped = focusableElements(panelRef.current)
      if (trapped.length === 0) {
        return
      }

      const first = trapped[0]
      const last = trapped[trapped.length - 1]
      const active = document.activeElement

      if (event.shiftKey && active === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)

    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previouslyFocusedRef.current?.focus()
    }
  }, [onCancel])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <button
        type="button"
        aria-label="Close install dialog"
        className="absolute inset-0"
        onClick={onCancel}
        disabled={isInstalling}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="study-assistant-install-title"
        className="relative z-10 flex max-h-[90vh] w-full max-w-2xl flex-col rounded-xl border border-app-border bg-app-elevated shadow-xl"
      >
        <div className="border-b border-app-border px-5 py-4">
          <h2 id="study-assistant-install-title" className="text-lg font-semibold text-app-text">
            Install AI Study Assistant
          </h2>
          <p className="mt-2 text-sm text-app-muted">
            Select the channels and resources the assistant may use when answering questions in
            #questions. The assistant only uses checked sources.
          </p>
        </div>

        <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4">
          {alreadyInstalled ? (
            <p className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-100">
              AI Study Assistant is already installed in this Study Server.
            </p>
          ) : (
            <p className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-100">
              The assistant will only use checked sources. Uncheck a source to exclude its content.
            </p>
          )}

          <fieldset className="space-y-3" disabled={alreadyInstalled || isInstalling}>
            <legend className="text-xs font-semibold uppercase tracking-[0.12em] text-app-muted">
              Permitted sources
            </legend>

            {preview.candidates.studyServerChannels.length > 0 ? (
              <section className="rounded-lg border border-app-border bg-app-bg p-3">
                <h3 className="text-sm font-medium text-app-text">Study Server channels</h3>
                <ul className="mt-2 space-y-2">
                  {preview.candidates.studyServerChannels.map((channel) => {
                    const key = grantKey('STUDY_SERVER_CHANNEL', channel.id)
                    return (
                      <GrantCheckboxRow
                        key={key}
                        id={key}
                        label={`#${channel.name}`}
                        checked={selectedKeys.has(key)}
                        onChange={(checked) => onToggleKey(key, checked)}
                      />
                    )
                  })}
                </ul>
              </section>
            ) : null}

            {preview.candidates.courses.map((course) => {
              const courseResources = preview.courseResources.filter(
                (resource) => resource.courseId === course.id,
              )

              return (
                <section
                  key={course.id}
                  className="rounded-lg border border-app-border bg-app-bg p-3"
                >
                  <GrantCheckboxRow
                    id={grantKey('COURSE', course.id)}
                    label={course.title}
                    checked={selectedKeys.has(grantKey('COURSE', course.id))}
                    onChange={(checked) => onToggleKey(grantKey('COURSE', course.id), checked)}
                  />

                  <div className="mt-3 space-y-3 border-l border-app-border pl-4">
                    {course.channels.length > 0 ? (
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-[0.1em] text-app-muted">
                          Channels
                        </p>
                        <ul className="mt-2 space-y-2">
                          {course.channels.map((channel) => {
                            const key = grantKey('COURSE_CHANNEL', channel.id)
                            return (
                              <GrantCheckboxRow
                                key={key}
                                id={key}
                                label={`#${channel.name}`}
                                checked={selectedKeys.has(key)}
                                onChange={(checked) => onToggleKey(key, checked)}
                              />
                            )
                          })}
                        </ul>
                      </div>
                    ) : null}

                    {course.cohorts.length > 0 ? (
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-[0.1em] text-app-muted">
                          Cohorts
                        </p>
                        <ul className="mt-2 space-y-2">
                          {course.cohorts.map((cohort) => {
                            const key = grantKey('COHORT', cohort.id)
                            return (
                              <GrantCheckboxRow
                                key={key}
                                id={key}
                                label={cohort.name}
                                checked={selectedKeys.has(key)}
                                onChange={(checked) => onToggleKey(key, checked)}
                              />
                            )
                          })}
                        </ul>
                      </div>
                    ) : null}

                    {courseResources.length > 0 ? (
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-[0.1em] text-app-muted">
                          AI-approved resources
                        </p>
                        <ul className="mt-2 space-y-2">
                          {courseResources.map((resource) => {
                            const key = grantKey('COURSE_RESOURCE', resource.id)
                            return (
                              <GrantCheckboxRow
                                key={key}
                                id={key}
                                label={`${resource.title} (${resource.fileName})`}
                                checked={selectedKeys.has(key)}
                                onChange={(checked) => onToggleKey(key, checked)}
                              />
                            )
                          })}
                        </ul>
                      </div>
                    ) : null}
                  </div>
                </section>
              )
            })}

            {allGrantKeysFromPreview(preview).size === 0 ? (
              <p className="text-sm text-app-muted">
                No install candidates yet. Add courses, channels, or AI-approved resources before
                installing.
              </p>
            ) : null}
          </fieldset>

          <p className="rounded-lg border border-sky-500/40 bg-sky-500/10 px-3 py-2 text-xs text-sky-100">
            The assistant may still answer using general knowledge when no checked source matches,
            but grounded answers cite only approved materials.
          </p>

          {installError ? (
            <p role="alert" className="text-sm text-red-300">
              {installError}
            </p>
          ) : null}
        </div>

        <div className="flex flex-wrap justify-end gap-2 border-t border-app-border px-5 py-4">
          <Button type="button" variant="secondary" disabled={isInstalling} onClick={onCancel}>
            Cancel
          </Button>
          <Button type="button" disabled={!canConfirm} onClick={onConfirm}>
            {isInstalling ? 'Installing…' : 'Confirm install'}
          </Button>
        </div>
      </div>
    </div>
  )
}

function GrantCheckboxRow({
  id,
  label,
  checked,
  onChange,
}: {
  id: string
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <label htmlFor={id} className="flex items-start gap-2 text-sm text-app-text">
      <input
        id={id}
        type="checkbox"
        className="mt-0.5"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>{label}</span>
    </label>
  )
}
