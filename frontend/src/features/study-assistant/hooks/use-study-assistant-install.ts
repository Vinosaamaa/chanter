import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'

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

type PreviewState = {
  contextKey: string
  preview: StudyAssistantInstallPreview
}

export function useStudyAssistantInstallFlow({
  studyServerId,
  instructorUserId,
}: {
  studyServerId: string | undefined
  instructorUserId: string | undefined
}) {
  const queryClient = useQueryClient()
  const contextKey =
    studyServerId && instructorUserId ? `${studyServerId}:${instructorUserId}` : null
  const [previewState, setPreviewState] = useState<PreviewState | null>(null)
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set())
  const [installError, setInstallError] = useState<string | null>(null)
  const [isOpening, setIsOpening] = useState(false)
  const openRequestIdRef = useRef(0)
  const closeDialogRef = useRef<() => void>(() => {})

  const preview =
    previewState && previewState.contextKey === contextKey ? previewState.preview : null

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
      closeDialogRef.current()
    },
    onError: (error) => {
      setInstallError(studyAssistantInstallErrorMessage(error))
    },
  })

  const { mutate, reset, isPending } = installMutation

  const closeDialog = useCallback(() => {
    setPreviewState(null)
    setSelectedKeys(new Set())
    setInstallError(null)
    setIsOpening(false)
    reset()
  }, [reset])

  useEffect(() => {
    closeDialogRef.current = closeDialog
  }, [closeDialog])

  useEffect(() => {
    openRequestIdRef.current += 1
  }, [contextKey])

  const openInstallDialog = useCallback(async () => {
    if (!studyServerId || !instructorUserId || !contextKey) {
      return
    }

    const requestId = ++openRequestIdRef.current
    const requestContextKey = contextKey
    setIsOpening(true)
    setInstallError(null)

    try {
      const nextPreview = await fetchStudyAssistantInstallPreview(studyServerId, instructorUserId)
      if (requestId !== openRequestIdRef.current || requestContextKey !== contextKey) {
        return
      }

      setPreviewState({ contextKey: requestContextKey, preview: nextPreview })
      setSelectedKeys(allGrantKeysFromPreview(nextPreview))
    } catch (error) {
      if (requestId !== openRequestIdRef.current || requestContextKey !== contextKey) {
        return
      }

      setInstallError(studyAssistantInstallErrorMessage(error))
      setPreviewState(null)
    } finally {
      if (requestId === openRequestIdRef.current) {
        setIsOpening(false)
      }
    }
  }, [contextKey, instructorUserId, studyServerId])

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
    mutate()
  }, [mutate, preview, selectedKeys.size])

  return {
    preview,
    selectedKeys,
    installError,
    isDialogOpen: preview !== null,
    isOpening,
    isInstalling: isPending,
    openInstallDialog,
    closeDialog,
    toggleGrantKey,
    confirmInstall,
  }
}
