import type {
  StudyAssistantGrantSelection,
  StudyAssistantGrantType,
  StudyAssistantInstallPreview,
} from './study-assistant-types'

export function grantKey(grantType: StudyAssistantGrantType, grantTargetId: string): string {
  return `${grantType}:${grantTargetId}`
}

export function allGrantKeysFromPreview(preview: StudyAssistantInstallPreview): Set<string> {
  const keys = new Set<string>()

  for (const channel of preview.candidates.studyServerChannels) {
    keys.add(grantKey('STUDY_SERVER_CHANNEL', channel.id))
  }

  for (const course of preview.candidates.courses) {
    keys.add(grantKey('COURSE', course.id))
    for (const cohort of course.cohorts) {
      keys.add(grantKey('COHORT', cohort.id))
    }
    for (const channel of course.channels) {
      keys.add(grantKey('COURSE_CHANNEL', channel.id))
    }
  }

  for (const resource of preview.courseResources) {
    keys.add(grantKey('COURSE_RESOURCE', resource.id))
  }

  return keys
}

export function grantsFromSelectedKeys(
  preview: StudyAssistantInstallPreview,
  selectedKeys: Set<string>,
): StudyAssistantGrantSelection[] {
  const grants: StudyAssistantGrantSelection[] = []

  for (const channel of preview.candidates.studyServerChannels) {
    const key = grantKey('STUDY_SERVER_CHANNEL', channel.id)
    if (selectedKeys.has(key)) {
      grants.push({ grantType: 'STUDY_SERVER_CHANNEL', grantTargetId: channel.id })
    }
  }

  for (const course of preview.candidates.courses) {
    const courseKey = grantKey('COURSE', course.id)
    if (selectedKeys.has(courseKey)) {
      grants.push({ grantType: 'COURSE', grantTargetId: course.id })
    }

    for (const cohort of course.cohorts) {
      const key = grantKey('COHORT', cohort.id)
      if (selectedKeys.has(key)) {
        grants.push({ grantType: 'COHORT', grantTargetId: cohort.id })
      }
    }

    for (const channel of course.channels) {
      const key = grantKey('COURSE_CHANNEL', channel.id)
      if (selectedKeys.has(key)) {
        grants.push({ grantType: 'COURSE_CHANNEL', grantTargetId: channel.id })
      }
    }
  }

  for (const resource of preview.courseResources) {
    const key = grantKey('COURSE_RESOURCE', resource.id)
    if (selectedKeys.has(key)) {
      grants.push({ grantType: 'COURSE_RESOURCE', grantTargetId: resource.id })
    }
  }

  return grants
}

export function grantLabel(grantType: StudyAssistantGrantType | string): string {
  return grantType.replaceAll('_', ' ').toLowerCase()
}
