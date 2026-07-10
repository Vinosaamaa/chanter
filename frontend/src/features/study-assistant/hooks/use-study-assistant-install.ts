import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'

import { ApiError } from '../../../lib/api-client'
import {
  fetchStudyAssistantInstallPreview,
  installStudyAssistant,
  studyAssistantInstallErrorMessage,
} from '../study-assistant-api'
import { allGrantKeysFromPreview, grantsFromSelectedKeys } from '../study-assistant-grants'
import type { StudyAssistantInstallPreview } from '../study-assistant-types'

export function studyAssistantPresenceQueryKey(
  studyServerId: string | undefined,
  userId: string | undefined,
) {
  return ['study-assistant-presence', studyServerId, userId] as const
}

export function useStudyAssistantInstallFlow({
  studyServerId,
  instructorUserId,
}: {
  studyServerId: string | undefined
  instructorUserId: string | undefined
}) {
  const queryClient = useQueryClient()
  const [preview, setPreview] = useState<StudyAssistantInstallPreview | null>(null)
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set())
  const [installError, setInstallError] = useState<string | null>(null)
  const [isOpening, setIsOpening] = useState(false)

  const installMutation = useMutation({
    mutationFn: async () => {
      if (!studyServerId || !instructorUserId || !preview) {
        throw new Error('Install preview is not ready.')
      }

      return installStudyAssistant(
        studyServerId,
        instructorUserId,
        grantsFromSelectedKeys(preview, selectedKeys),
      )
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: studyAssistantPresenceQueryKey(studyServerId, instructorUserId),
      })
      closeDialog()
    },
    onError: (error) => {
      setInstallError(studyAssistantInstallErrorMessage(error))
    },
  })

  const closeDialog = useCallback(() => {
    setPreview(null)
    setSelectedKeys(new Set())
    setInstallError(null)
    setIsOpening(false)
    installMutation.reset()
  }, [installMutation])

  const openInstallDialog = useCallback(async () => {
    if (!studyServerId || !instructorUserId) {
      return
    }

    setIsOpening(true)
    setInstallError(null)

    try {
      const nextPreview = await fetchStudyAssistantInstallPreview(studyServerId, instructorUserId)
      setPreview(nextPreview)
      setSelectedKeys(allGrantKeysFromPreview(nextPreview))
    } catch (error) {
      setInstallError(
        error instanceof ApiError && error.status === 403
          ? studyAssistantInstallErrorMessage(error)
          : studyAssistantInstallErrorMessage(error),
      )
      setPreview(null)
    } finally {
      setIsOpening(false)
    }
  }, [instructorUserId, studyServerId])

  const toggleGrantKey = useCallback((key: string, checked: boolean) => {
    setSelectedKeys((current) => {
      const next = new Set(current)
      if (checked) {
        next.add(key)
      } else {
        next.delete(key)
      }
      return next
    })
  }, [])

  const confirmInstall = useCallback(() => {
    if (!preview || preview.alreadyInstalled || selectedKeys.size === 0) {
      return
    }
    setInstallError(null)
    installMutation.mutate()
  }, [installMutation, preview, selectedKeys.size])

  return {
    preview,
    selectedKeys,
    installError,
    isDialogOpen: preview !== null,
    isOpening,
    isInstalling: installMutation.isPending,
    openInstallDialog,
    closeDialog,
    toggleGrantKey,
    confirmInstall,
  }
}
