import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  addTeachingAssistant,
  assignTeachingAssistant,
  cancelCohortInvitation,
  createCohortInvitation,
  fetchCohortRoster,
  removeCohortEnrollment,
  removeTeachingAssistant,
} from './cohort-roster-api'

export const cohortRosterQueryKey = (cohortId: string | undefined) =>
  ['cohort-roster', cohortId] as const

export function useCohortRosterQuery(cohortId: string | undefined) {
  return useQuery({
    queryKey: cohortRosterQueryKey(cohortId),
    queryFn: () => fetchCohortRoster(cohortId!),
    enabled: Boolean(cohortId),
  })
}

export function useCohortRoster(cohortId: string | undefined) {
  const queryClient = useQueryClient()
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: cohortRosterQueryKey(cohortId) })
  }
  const query = useCohortRosterQuery(cohortId)
  const invite = useMutation({
    mutationFn: (email: string) => createCohortInvitation(cohortId!, email),
    onSuccess: refresh,
  })
  const addTa = useMutation({
    mutationFn: (userId: string) => addTeachingAssistant(cohortId!, userId),
    onSuccess: refresh,
  })
  const removeTa = useMutation({
    mutationFn: (userId: string) => removeTeachingAssistant(cohortId!, userId),
    onSuccess: refresh,
  })
  const assignTa = useMutation({
    mutationFn: (input: { learnerUserIds: string[]; teachingAssistantUserId: string | null }) =>
      assignTeachingAssistant(cohortId!, input.learnerUserIds, input.teachingAssistantUserId),
    onSuccess: refresh,
  })
  const removeEnrollment = useMutation({
    mutationFn: (userId: string) => removeCohortEnrollment(cohortId!, userId),
    onSuccess: refresh,
  })
  const cancelInvitation = useMutation({
    mutationFn: (invitationId: string) => cancelCohortInvitation(cohortId!, invitationId),
    onSuccess: refresh,
  })

  return { query, invite, addTa, removeTa, assignTa, removeEnrollment, cancelInvitation }
}
