import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../../stores/auth-store'
import { logout as logoutApi } from '../auth-api'

export function useSignOut() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useCallback(async () => {
    const refreshToken = useAuthStore.getState().refreshToken

    await queryClient.cancelQueries()
    queryClient.clear()
    useAuthStore.getState().clearSession()
    navigate('/sign-in', { replace: true, state: null })

    if (refreshToken) {
      try {
        await logoutApi(refreshToken)
      } catch {
        // The local account boundary must hold even when token revocation is unavailable.
      }
    }
  }, [navigate, queryClient])
}
