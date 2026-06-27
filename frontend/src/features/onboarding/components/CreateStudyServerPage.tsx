import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { Button } from '../../../components/ui/button'
import { Card, CardDescription, CardTitle } from '../../../components/ui/card'
import { formatUserFacingApiError, isUnauthorizedApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'

import { createStudyServer } from '../onboarding-api'

export function CreateStudyServerPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setError('Enter a name for your Study Server.')
      return
    }

    setIsSubmitting(true)
    setError(null)

    try {
      const created = await createStudyServer(trimmed)
      await queryClient.invalidateQueries({ queryKey: ['study-servers'] })
      navigate(`/app/servers/${created.id}/home`, { replace: true })
    } catch (caught) {
      if (isUnauthorizedApiError(caught)) {
        clearSession()
        navigate('/sign-in', {
          replace: true,
          state: { from: '/app/onboarding/create-study-server' },
        })
        return
      }
      setError(formatUserFacingApiError(caught, 'Unable to create Study Server.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-app-bg px-6 py-12 text-app-text">
      <Card className="w-full max-w-lg">
        <CardTitle>Create your Study Server</CardTitle>
        <CardDescription>
          Name your learning community. We will add default channels (#announcements, #general, and a
          study voice room).
        </CardDescription>

        <form className="mt-6 flex flex-col gap-4" onSubmit={onSubmit}>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-app-muted">Study Server name</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
              disabled={isSubmitting}
              placeholder="e.g. Java Spring Study Group"
              className="rounded-md border border-app-border bg-app-bg px-3 py-2 text-app-text"
            />
          </label>

          {error ? (
            <p role="alert" className="text-sm text-red-300">
              {error}
            </p>
          ) : null}

          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Creating…' : 'Create Study Server'}
          </Button>
        </form>
      </Card>
    </div>
  )
}
