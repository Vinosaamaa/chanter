import { describe, expect, it } from 'vitest'

import type { StudyServerNavigation } from './types'
import {
  channelBreadcrumb,
  channelDescription,
  courseChannelGroup,
  resolveShellContextPanelKind,
} from './shell-routes'

const navigation: StudyServerNavigation = {
  studyServerId: 'server-1',
  studyServerName: 'Demo Server',
  canViewFullCatalog: true,
  studyServerChannels: [
    { id: 'study-voice-1', name: 'study-room', kind: 'VOICE' },
    { id: 'study-text-1', name: 'lobby', kind: 'TEXT' },
  ],
  courses: [
    {
      id: 'course-1',
      title: 'CS 101',
      cohorts: [{ id: 'cohort-1', name: 'Summer 2026' }],
      channels: [
        { id: 'ch-announce', name: 'announcements', kind: 'TEXT' },
        { id: 'ch-general', name: 'general', kind: 'TEXT' },
        { id: 'ch-questions', name: 'questions', kind: 'TEXT' },
        { id: 'ch-resources', name: 'resources', kind: 'TEXT' },
        { id: 'ch-voice', name: 'study-room', kind: 'VOICE' },
      ],
    },
  ],
}

describe('shell-routes context helpers', () => {
  it('groups course channels for sidebar sections', () => {
    expect(courseChannelGroup({ id: '1', name: 'announcements', kind: 'TEXT' })).toBe('information')
    expect(courseChannelGroup({ id: '2', name: 'general', kind: 'TEXT' })).toBe('text')
    expect(courseChannelGroup({ id: '3', name: 'study-room', kind: 'VOICE' })).toBe('voice')
  })

  it('resolves context panel kind by channel route', () => {
    expect(
      resolveShellContextPanelKind(
        '/app/servers/server-1/course-channels/ch-general',
        'ch-general',
        navigation,
      ),
    ).toBe('general')

    expect(
      resolveShellContextPanelKind(
        '/app/servers/server-1/course-channels/ch-questions',
        'ch-questions',
        navigation,
      ),
    ).toBe('questions')

    expect(
      resolveShellContextPanelKind(
        '/app/servers/server-1/course-channels/ch-resources',
        'ch-resources',
        navigation,
      ),
    ).toBe('resources')

    expect(
      resolveShellContextPanelKind(
        '/app/servers/server-1/study-channels/study-voice-1',
        'study-voice-1',
        navigation,
      ),
    ).toBe('voice')
  })

  it('builds breadcrumbs and descriptions', () => {
    expect(channelBreadcrumb(navigation, 'course', 'ch-general')).toEqual({
      courseTitle: 'CS 101',
      channelName: 'general',
    })

    expect(channelDescription('course', 'general')).toContain('Course-wide chat')
  })
})
