/** Synthetic API responses exclusively imported by vite.visual.config.ts. Never product data. */
const serverId = 'visual-study'
const courseId = 'visual-course-0'
const cohortId = 'visual-cohort'
const learner = 'visual-learner'
const peer = 'visual-peer'
const instructor = 'visual-instructor'
const courseTitle = 'CS 101 — Foundations of computer science'
const course = `/app/servers/${serverId}/courses/${courseId}`
const community = `/app/servers/${serverId}/community`
const now = new Date()
const earlier = new Date(now.getTime() - 90 * 60_000).toISOString()
const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 14).toISOString()
const later = new Date(new Date(tomorrow).getTime() + 60 * 60_000).toISOString()
const profiles = [{ userId: learner, displayName: 'Sam Rivera' }, { userId: peer, displayName: 'Alexandra Montgomery-Williams' }, { userId: instructor, displayName: 'Dr. Ada Chen' }]
const event = { id: 'visual-event', studyServerId: serverId, title: 'Community study circle: making difficult ideas clear', description: 'Bring a question, a sketch or a problem you are working through. We will compare approaches in small groups.', location: 'Community lounge', startsAt: tomorrow, endsAt: later, capacity: 30, visibility: 'HUB', courseId: null, cohortId: null, createdByUserId: instructor, status: 'SCHEDULED', goingCount: 12, interestedCount: 5, viewerRsvp: 'GOING', canEdit: false, sharePath: `${community}/events?event=visual-event`, calendarPath: '/app/calendar?event=visual-event', icsPath: `/api/v1/community-events/visual-event/calendar.ics` }
const officeHours = { id: 'visual-office', cohortId, voiceChannelId: 'visual-voice', scheduledByUserId: instructor, startsAt: tomorrow, endsAt: later, status: 'SCHEDULED', createdAt: earlier }
const notification = { id: 'visual-notification', userId: learner, kind: 'ANNOUNCEMENT', filterBucket: 'ANNOUNCEMENTS', title: 'A fresh starting point for this week', bodyPreview: 'This week we are exploring how small, testable steps help us solve larger problems. Read the worked example before our next session, and bring one question you would like to unpack together.', courseLabel: 'CS 101', href: `${community}/announcements`, sourceType: 'ANNOUNCEMENT', sourceId: 'visual-announcement', studyServerId: serverId, courseId, cohortId, channelId: null, createdAt: earlier, readAt: null, doneAt: null, unread: true }
const rosterMember = (userId: string, role: string) => ({ userId, invitationId: null, displayName: profiles.find(p => p.userId === userId)?.displayName ?? 'Jordan Park', email: null, role, status: 'ENROLLED', assignedTeachingAssistantUserId: null, enrolledAt: earlier })

export function workspaceFixture(path: string, empty: boolean, staff: boolean): unknown {
  if (path.endsWith('/overview-summary')) return { progress: null, progressUnavailableReason: null, thisWeek: empty ? [] : [{ id: 'week-office', kind: 'OFFICE_HOURS', title: 'Bring your questions to Office Hours', detail: 'Tomorrow, 2:00 PM', href: `${course}/office-hours` }, { id: 'week-resource', kind: 'RESOURCE', title: 'Read the worked example', detail: 'Foundations and problem solving', href: `${course}/resources` }], recentActivity: empty ? [] : [{ id: 'recent-resource', kind: 'RESOURCE', title: 'Problem-solving field notes', detail: 'Added by Dr. Ada Chen', href: `${course}/resources` }, { id: 'recent-question', kind: 'QUESTION', title: 'How do we choose a useful first step?', detail: 'A question from Alexandra', href: `${course}/questions` }], upNext: empty ? [] : [{ id: 'next-office', kind: 'OFFICE_HOURS', title: 'Office Hours', detail: 'Tomorrow, 2:00 PM', actionLabel: 'View', href: `${course}/office-hours` }], partialFailures: [] }
  if (path.endsWith('/channel-message-access')) return { channelId: 'visual-general', channelName: 'general', canReadMessages: true, canPostMessages: true }
  if (path.endsWith('/messages')) return { messages: empty ? [] : [{ id: 'visual-message-1', channelId: 'visual-general', senderUserId: instructor, body: 'Welcome back, everyone. What changed in your approach after trying the worked example?', createdAt: earlier }, { id: 'visual-message-2', channelId: 'visual-general', senderUserId: peer, body: 'I started by sketching the inputs and outputs. It made the smaller steps much easier to see. I have added my notes to the project discussion.', createdAt: earlier }, { id: 'visual-message-3', channelId: 'visual-general', senderUserId: learner, body: 'The sketch helped me too. I still have a question about choosing which part to tackle first.', createdAt: earlier }] }
  if (path.endsWith('/friendships')) return { friends: empty ? [] : [{ friendUserId: peer, friendsSince: earlier }, { friendUserId: instructor, friendsSince: earlier }] }
  if (path.endsWith('/friend-requests')) return { incoming: [], outgoing: [] }
  if (path.endsWith('/user-blocks')) return { blockedUserIds: [] }
  if (path.endsWith('/co-members')) return { coMembers: [{ userId: peer, sharedStudyServerName: 'Open Learning Collective' }] }
  if (path.endsWith('/profiles/query')) return { profiles }
  if (path.endsWith('/direct-messages')) return { messages: empty ? [] : [{ id: 'visual-dm-1', senderUserId: peer, recipientUserId: learner, body: 'Would you like to compare our notes before Office Hours tomorrow?', sentAt: earlier }, { id: 'visual-dm-2', senderUserId: learner, recipientUserId: peer, body: 'Yes! I have a few questions about breaking down the example. Let us start with the sketch.', sentAt: earlier }, { id: 'visual-dm-3', senderUserId: peer, recipientUserId: learner, body: 'Sounds good. I will bring the version with my annotations so we can see where our approaches differ.', sentAt: earlier }] }
  if (path.endsWith('/notifications')) return { notifications: empty ? [] : [notification, { ...notification, id: 'visual-notification-2', filterBucket: 'MENTIONS', title: 'Alexandra mentioned you in a question', bodyPreview: 'Your sketch helped clarify the first step. How did you decide what to leave out?', unread: false }] }
  if (path.endsWith('/calendar')) return { items: empty ? [] : [{ id: 'visual-calendar-office', type: 'OFFICE_HOURS', title: 'Office Hours with Dr. Ada Chen', contextLabel: courseTitle, startsAt: tomorrow, endsAt: later, href: `${course}/office-hours`, actionLabel: 'View session', actionKind: 'OPEN', viewerRsvp: null, studyServerId: serverId, courseId, cohortId, sourceId: 'visual-office' }, { id: 'visual-calendar-event', type: 'EVENT', title: event.title, contextLabel: 'Open Learning Collective', startsAt: tomorrow, endsAt: later, href: event.sharePath, actionLabel: 'View event', actionKind: 'OPEN', viewerRsvp: 'GOING', studyServerId: serverId, courseId: null, cohortId: null, sourceId: event.id }], notes: [] }
  if (path.endsWith('/announcements')) return { announcements: empty ? [] : [{ id: 'visual-announcement', studyServerId: serverId, authorUserId: instructor, authorDisplayName: 'Dr. Ada Chen', title: notification.title, body: notification.bodyPreview, status: 'PUBLISHED', createdAt: earlier, updatedAt: earlier, likeCount: 8, viewerLiked: false, canEdit: staff }, { id: 'visual-announcement-2', studyServerId: serverId, authorUserId: peer, authorDisplayName: 'Alexandra Montgomery-Williams', title: 'Study circle this weekend', body: 'A few of us are getting together to compare notes and work through the parts we found tricky. Everyone is welcome, wherever you are in the Course.', status: 'PUBLISHED', createdAt: earlier, updatedAt: earlier, likeCount: 4, viewerLiked: false, canEdit: false }] }
  if (path.endsWith('/course-catalog')) return { courses: [] }
  if (path.endsWith('/member-summary')) return { memberCount: 28, preview: profiles }
  if (path.endsWith('/members')) return { members: profiles.map(p => ({ ...p, email: null, role: p.userId === instructor ? 'INSTRUCTOR' : 'LEARNER', staff: p.userId === instructor })), filteredTotal: 3, memberCount: 28 }
  if (path.endsWith('/events')) return { events: empty ? [] : [event] }
  if (path.endsWith('/office-hours-access')) return { cohortId, courseId, studyServerId: serverId, canScheduleOfficeHours: staff, canJoinOfficeHours: true, canManageOfficeHours: staff }
  if (path.endsWith('/office-hours')) return { officeHoursSessions: empty ? [] : [officeHours] }
  if (path.endsWith('/participants')) return { participants: [] }
  if (path.endsWith('/waitlist')) return { waitlistEntries: [] }
  if (path.endsWith('/resource-access')) return { courseId, canUploadCourseResource: staff, canViewCourseResources: true }
  if (path.endsWith('/course-resources')) return { courseResources: empty ? [] : [{ id: 'visual-resource', courseId, title: 'Problem-solving field notes', fileName: 'problem-solving-field-notes.pdf', contentType: 'application/pdf', byteSize: 348160, aiApproved: true, uploadedByUserId: instructor, createdAt: earlier }, { id: 'visual-resource-2', courseId, title: 'Worked example: from a sketch to a solution', fileName: 'worked-example.pdf', contentType: 'application/pdf', byteSize: 524288, aiApproved: true, uploadedByUserId: instructor, createdAt: earlier }] }
  if (path.endsWith('/roster')) return { cohortId, instructor: rosterMember(instructor, 'INSTRUCTOR'), teachingAssistants: [], learners: [rosterMember(peer, 'LEARNER'), rosterMember(learner, 'LEARNER')], learnerCount: 2, teachingAssistantCount: 0, pendingCount: 0, limit: 100, offset: 0 }
  if (path.endsWith('/support-questions')) return { supportQuestions: empty ? [] : [{ id: 'visual-question', channelMessageId: 'visual-question-message', channelId: 'visual-questions', senderUserId: peer, body: 'How do we choose a useful first step when the problem feels too large?', status: 'UNANSWERED', createdAt: earlier }] }
  if (path.endsWith('/assistant-answer')) return null
  if (path.endsWith('/replies')) return { replies: [] }
  if (path.endsWith('/study-assistant')) return { studyServerId: serverId, installed: false, grants: [] }
  if (path.endsWith('/ta-queue-access')) return { cohortId, courseId, studyServerId: serverId, canAddToTaQueue: true, canManageTaQueue: staff }
  if (path.endsWith('/ta-queue')) return { taQueueItems: [] }
  if (path.endsWith('/faq-candidates')) return { faqCandidates: [] }
  if (path.endsWith('/approved-faqs')) return { approvedFaqs: [] }
  if (path.endsWith('/instructor-dashboard')) return { studyServerId: serverId, planTier: 'PRO', unansweredSupportQuestions: 3, repeatedQuestionGroups: 1, approvedFaqCount: 8, openTaQueueItems: 2, liveOfficeHoursSessions: 0, scheduledOfficeHoursSessions: 1, officeHoursWaitlistEntries: 0, aiInvocationCount: 42, aiInvocationLimit: 100, remainingAiInvocations: 58, quotaExhausted: false, lowConfidenceHandoffs: 1, courses: [{ courseId, title: courseTitle, questionChannelId: 'visual-questions', cohorts: [{ cohortId, name: 'Autumn cohort', openTaQueueItems: 2 }], unansweredSupportQuestions: 3, repeatedQuestionGroups: 1, approvedFaqCount: 8, openTaQueueItems: 2 }] }
  if (path === `/api/v1/study-servers/${serverId}`) return { id: serverId, name: 'Open Learning Collective', ownerRole: { userId: learner, role: 'OWNER' }, planTier: 'PRO' }
  return undefined
}
