export function formatFriendLabel(userId: string): string {
  return `Friend ${userId.slice(0, 8)}`
}
