export type SourceType = 'USER' | 'DM' | 'MESSAGE' | 'RESOURCE' | 'STUDY_SERVER'
export type Report = { id: string; targetType: SourceType; targetId: string; reason: string; status: string; resolution: string | null; createdAt: string }
export const sourceNames: Record<SourceType, string> = { USER: 'Account', DM: 'Direct Message', MESSAGE: 'Channel message', RESOURCE: 'Course Resource', STUDY_SERVER: 'Study Server' }
export function sourceType(value: string | null): value is SourceType { return value !== null && Object.hasOwn(sourceNames, value) }
