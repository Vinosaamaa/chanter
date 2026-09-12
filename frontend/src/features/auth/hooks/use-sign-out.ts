import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'

import { signOutBrowserSession } from '../browser-session'

export function useSignOut() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useCallback(async () => {
    await queryClient.cancelQueries()
    queryClient.clear()
    const signOut = signOutBrowserSession()
    navigate('/sign-in', { replace: true, state: null })

    try {
      await signOut
    } catch {
      navigate('/sign-in', { replace: true, state: { logoutFailed: true } })
    }
  }, [navigate, queryClient])
}
