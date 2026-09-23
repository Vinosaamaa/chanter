import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'

import { signOutBrowserSession } from '../browser-session'

export function useSignOut(returnTo?: string) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useCallback(async () => {
    await queryClient.cancelQueries()
    queryClient.clear()
    const signOut = signOutBrowserSession()
    const state = returnTo ? { from: returnTo } : null
    navigate('/sign-in', { replace: true, state })

    try {
      await signOut
    } catch {
      navigate('/sign-in', { replace: true, state: { ...state, logoutFailed: true } })
    }
  }, [navigate, queryClient, returnTo])
}
