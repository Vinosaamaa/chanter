import type { CoMember, PublicUserProfile } from './types'

export type CoMemberRelationshipState =
  | 'available'
  | 'friend'
  | 'incoming'
  | 'outgoing'
  | 'blocked'

export type CoMemberDirectoryEntry = CoMember & {
  displayName: string
  state: CoMemberRelationshipState
}

export function profilesByUserId(
  profiles: PublicUserProfile[],
): Record<string, PublicUserProfile> {
  return Object.fromEntries(profiles.map((profile) => [profile.userId, profile]))
}

export function buildCoMemberDirectory({
  coMembers,
  profilesById,
  friendUserIds,
  incomingUserIds,
  outgoingUserIds,
  blockedUserIds,
}: {
  coMembers: CoMember[]
  profilesById: Record<string, PublicUserProfile>
  friendUserIds: Set<string>
  incomingUserIds: Set<string>
  outgoingUserIds: Set<string>
  blockedUserIds: Set<string>
}): CoMemberDirectoryEntry[] {
  return coMembers
    .flatMap((coMember) => {
      const profile = profilesById[coMember.userId]
      if (!profile) return []

      let state: CoMemberRelationshipState = 'available'
      if (blockedUserIds.has(coMember.userId)) state = 'blocked'
      else if (friendUserIds.has(coMember.userId)) state = 'friend'
      else if (incomingUserIds.has(coMember.userId)) state = 'incoming'
      else if (outgoingUserIds.has(coMember.userId)) state = 'outgoing'

      return [{ ...coMember, displayName: profile.displayName, state }]
    })
    .sort((first, second) => first.displayName.localeCompare(second.displayName))
}
