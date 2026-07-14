import { describe, expect, it } from 'vitest'

import { buildCoMemberDirectory, profilesByUserId } from './friend-directory'

describe('friend directory', () => {
  it('uses real profiles and truthful relationship-state precedence', () => {
    const profiles = profilesByUserId([
      { userId: 'available', displayName: 'Avery Available' },
      { userId: 'friend', displayName: 'Casey Friend' },
      { userId: 'incoming', displayName: 'Morgan Incoming' },
      { userId: 'outgoing', displayName: 'Noah Outgoing' },
      { userId: 'blocked', displayName: 'Taylor Blocked' },
    ])

    const entries = buildCoMemberDirectory({
      coMembers: [
        { userId: 'blocked', sharedStudyServerName: 'Spring Bootcamp Hub' },
        { userId: 'outgoing', sharedStudyServerName: 'Spring Bootcamp Hub' },
        { userId: 'incoming', sharedStudyServerName: 'Spring Bootcamp Hub' },
        { userId: 'friend', sharedStudyServerName: 'Spring Bootcamp Hub' },
        { userId: 'available', sharedStudyServerName: 'Spring Bootcamp Hub' },
        { userId: 'missing-profile', sharedStudyServerName: 'Spring Bootcamp Hub' },
      ],
      profilesById: profiles,
      friendUserIds: new Set(['friend']),
      incomingUserIds: new Set(['incoming', 'blocked']),
      outgoingUserIds: new Set(['outgoing']),
      blockedUserIds: new Set(['blocked']),
    })

    expect(entries.map(({ userId, displayName, state }) => ({ userId, displayName, state }))).toEqual([
      { userId: 'available', displayName: 'Avery Available', state: 'available' },
      { userId: 'friend', displayName: 'Casey Friend', state: 'friend' },
      { userId: 'incoming', displayName: 'Morgan Incoming', state: 'incoming' },
      { userId: 'outgoing', displayName: 'Noah Outgoing', state: 'outgoing' },
      { userId: 'blocked', displayName: 'Taylor Blocked', state: 'blocked' },
    ])
  })
})
