import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

type HealthResponse = {
  status: string
  service?: string
}

type Channel = {
  id: string
  name: string
  kind: 'TEXT' | 'VOICE'
}

type StudyServer = {
  id: string
  name: string
  ownerRole: {
    userId: string
    role: string
  }
  channels: Channel[]
}

type Course = {
  id: string
  title: string
  instructorRole: {
    userId: string
    role: string
  }
  cohort: {
    id: string
    name: string
  }
  channels: Channel[]
}

type VoicePresence = {
  channelId: string
  memberUserId: string
  canSpeak: boolean
  canListen: boolean
}

type FriendRequest = {
  id: string
  senderUserId: string
  recipientUserId: string
  status: string
}

type DirectMessage = {
  id: string
  senderUserId: string
  recipientUserId: string
  body: string
}

type HealthState = {
  gateway: string
  auth: string
  community: string
  message: string
}

const createUserId = () => {
  if ('crypto' in window && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID()
  }

  return '00000000-0000-4000-8000-000000000001'
}

function App() {
  const [health, setHealth] = useState<HealthState>({
    gateway: 'checking',
    auth: 'checking',
    community: 'checking',
    message: 'checking',
  })
  const [ownerUserId] = useState(createUserId)
  const [instructorUserId] = useState(createUserId)
  const [learnerUserId] = useState(createUserId)
  const [nonEnrolledUserId] = useState(createUserId)
  const [serverName, setServerName] = useState('Java Spring Study Group')
  const [courseTitle, setCourseTitle] = useState('Spring Boot Foundations')
  const [cohortName, setCohortName] = useState('Summer 2026')
  const [studyServer, setStudyServer] = useState<StudyServer | null>(null)
  const [course, setCourse] = useState<Course | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [isCreatingCourse, setIsCreatingCourse] = useState(false)
  const [isEnrolling, setIsEnrolling] = useState(false)
  const [isJoiningVoice, setIsJoiningVoice] = useState(false)
  const [isCheckingVoiceAccess, setIsCheckingVoiceAccess] = useState(false)
  const [isLeavingVoice, setIsLeavingVoice] = useState(false)
  const [accessResult, setAccessResult] = useState<string | null>(null)
  const [voiceResult, setVoiceResult] = useState<string | null>(null)
  const [voicePresences, setVoicePresences] = useState<VoicePresence[]>([])
  const [friendRequest, setFriendRequest] = useState<FriendRequest | null>(null)
  const [directMessageBody, setDirectMessageBody] = useState('Want to study together?')
  const [directMessages, setDirectMessages] = useState<DirectMessage[]>([])
  const [isSendingFriendRequest, setIsSendingFriendRequest] = useState(false)
  const [isAcceptingFriendRequest, setIsAcceptingFriendRequest] = useState(false)
  const [isDecliningFriendRequest, setIsDecliningFriendRequest] = useState(false)
  const [isSendingDirectMessage, setIsSendingDirectMessage] = useState(false)
  const [isRefreshingDirectMessages, setIsRefreshingDirectMessages] = useState(false)
  const [isCheckingDirectMessageAccess, setIsCheckingDirectMessageAccess] = useState(false)
  const [isBlockingUser, setIsBlockingUser] = useState(false)
  const [socialResult, setSocialResult] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/actuator/health')
      .then((response) => response.json())
      .then((data) => setHealth((current) => ({ ...current, gateway: data.status ?? 'unknown' })))
      .catch(() => setHealth((current) => ({ ...current, gateway: 'unreachable' })))

    fetch('/api/v1/auth/health')
      .then((response) => response.json())
      .then((data: HealthResponse) => setHealth((current) => ({ ...current, auth: data.status ?? 'unknown' })))
      .catch(() => setHealth((current) => ({ ...current, auth: 'unreachable' })))

    fetch('/api/v1/study-servers/00000000-0000-0000-0000-000000000000')
      .then((response) => {
        setHealth((current) => ({
          ...current,
          community: response.status === 404 ? 'ok' : response.ok ? 'ok' : 'unknown',
        }))
      })
      .catch(() => setHealth((current) => ({ ...current, community: 'unreachable' })))

    fetch(`/api/v1/direct-messages?viewerUserId=${ownerUserId}&peerUserId=${learnerUserId}`)
      .then((response) => {
        setHealth((current) => ({
          ...current,
          message: response.status === 403 || response.ok ? 'ok' : 'unknown',
        }))
      })
      .catch(() => setHealth((current) => ({ ...current, message: 'unreachable' })))
  }, [ownerUserId, learnerUserId])

  const textChannels = useMemo(
    () => studyServer?.channels.filter((channel) => channel.kind === 'TEXT') ?? [],
    [studyServer],
  )
  const voiceChannels = useMemo(
    () => studyServer?.channels.filter((channel) => channel.kind === 'VOICE') ?? [],
    [studyServer],
  )
  const selectedVoiceChannel = voiceChannels[0] ?? null

  const createStudyServer = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsCreating(true)
    setError(null)

    try {
      const response = await fetch('/api/v1/study-servers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: serverName, ownerUserId }),
      })

      if (!response.ok) {
        throw new Error(`Create failed with ${response.status}`)
      }

      const created: StudyServer = await response.json()
      const viewedResponse = await fetch(`/api/v1/study-servers/${created.id}`)

      if (!viewedResponse.ok) {
        throw new Error(`View failed with ${viewedResponse.status}`)
      }

      setStudyServer(await viewedResponse.json())
      setCourse(null)
      setAccessResult(null)
      setVoiceResult(null)
      setVoicePresences([])
      setHealth((current) => ({ ...current, community: 'ok' }))
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to create Study Server')
    } finally {
      setIsCreating(false)
    }
  }

  const createCourseAndCohort = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!studyServer) {
      return
    }

    setIsCreatingCourse(true)
    setError(null)
    setAccessResult(null)

    try {
      const response = await fetch(`/api/v1/study-servers/${studyServer.id}/courses`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ownerUserId,
          title: courseTitle,
          instructorUserId,
          cohortName,
        }),
      })

      if (!response.ok) {
        throw new Error(`Course create failed with ${response.status}`)
      }

      setCourse(await response.json())
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to create Course')
    } finally {
      setIsCreatingCourse(false)
    }
  }

  const enrollLearner = async () => {
    if (!course) {
      return
    }

    setIsEnrolling(true)
    setError(null)
    setAccessResult(null)

    try {
      const response = await fetch(`/api/v1/cohorts/${course.cohort.id}/enrollments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ instructorUserId, learnerUserId }),
      })

      if (!response.ok) {
        throw new Error(`Enroll failed with ${response.status}`)
      }

      const firstChannel = course.channels[0]
      const learnerAccess = await fetch(
        `/api/v1/course-channels/${firstChannel.id}?viewerUserId=${learnerUserId}`,
      )
      const outsiderAccess = await fetch(
        `/api/v1/course-channels/${firstChannel.id}?viewerUserId=${nonEnrolledUserId}`,
      )

      if (!learnerAccess.ok || outsiderAccess.status !== 403) {
        throw new Error('Enrollment access check failed')
      }

      setAccessResult('Learner can access Course Channels; non-enrolled user is blocked.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to enroll learner')
    } finally {
      setIsEnrolling(false)
    }
  }

  const refreshVoicePresences = async (channelId: string) => {
    const response = await fetch(
      `/api/v1/study-server-channels/${channelId}/voice-presences?viewerUserId=${ownerUserId}`,
    )

    if (!response.ok) {
      throw new Error(`Voice presence refresh failed with ${response.status}`)
    }

    const data: { presences: VoicePresence[] } = await response.json()
    setVoicePresences(data.presences)
    return data.presences
  }

  const joinVoiceChannel = async () => {
    if (!selectedVoiceChannel) {
      return
    }

    setIsJoiningVoice(true)
    setError(null)
    setVoiceResult(null)

    try {
      const response = await fetch(`/api/v1/study-server-channels/${selectedVoiceChannel.id}/voice-presences`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ memberUserId: ownerUserId }),
      })

      if (!response.ok) {
        throw new Error(`Voice join failed with ${response.status}`)
      }

      await refreshVoicePresences(selectedVoiceChannel.id)
      setVoiceResult('Owner joined the Voice Channel and can speak/listen.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to join Voice Channel')
    } finally {
      setIsJoiningVoice(false)
    }
  }

  const verifyNonMemberCannotJoinVoice = async () => {
    if (!selectedVoiceChannel) {
      return
    }

    setIsCheckingVoiceAccess(true)
    setError(null)
    setVoiceResult(null)

    try {
      const response = await fetch(`/api/v1/study-server-channels/${selectedVoiceChannel.id}/voice-presences`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ memberUserId: nonEnrolledUserId }),
      })

      if (response.status !== 403) {
        throw new Error(`Expected non-member voice join to fail with 403, got ${response.status}`)
      }

      await refreshVoicePresences(selectedVoiceChannel.id)
      setVoiceResult('Non-member is blocked from joining the Voice Channel.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to verify Voice Channel permissions')
    } finally {
      setIsCheckingVoiceAccess(false)
    }
  }

  const leaveVoiceChannel = async () => {
    if (!selectedVoiceChannel) {
      return
    }

    setIsLeavingVoice(true)
    setError(null)
    setVoiceResult(null)

    try {
      const response = await fetch(
        `/api/v1/study-server-channels/${selectedVoiceChannel.id}/voice-presences`,
        {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ memberUserId: ownerUserId }),
        },
      )

      if (!response.ok) {
        throw new Error(`Voice leave failed with ${response.status}`)
      }

      await refreshVoicePresences(selectedVoiceChannel.id)
      setVoiceResult('Owner left the Voice Channel.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to leave Voice Channel')
    } finally {
      setIsLeavingVoice(false)
    }
  }

  const refreshDirectMessages = async () => {
    setIsRefreshingDirectMessages(true)
    setError(null)

    try {
      const response = await fetch(
        `/api/v1/direct-messages?viewerUserId=${learnerUserId}&peerUserId=${ownerUserId}`,
      )

      if (!response.ok) {
        throw new Error(`Direct Message refresh failed with ${response.status}`)
      }

      const data: { messages: DirectMessage[] } = await response.json()
      setDirectMessages(data.messages)
      return data.messages
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to refresh Direct Messages')
      return []
    } finally {
      setIsRefreshingDirectMessages(false)
    }
  }

  const sendFriendRequest = async () => {
    setIsSendingFriendRequest(true)
    setError(null)
    setSocialResult(null)

    try {
      const response = await fetch('/api/v1/friend-requests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          senderUserId: ownerUserId,
          recipientUserId: learnerUserId,
        }),
      })

      if (!response.ok) {
        throw new Error(`Friend Request failed with ${response.status}`)
      }

      const created: FriendRequest = await response.json()
      setFriendRequest(created)
      setDirectMessages([])
      setSocialResult(`Friend Request sent (${created.status}).`)
      setHealth((current) => ({ ...current, message: 'ok' }))
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to send Friend Request')
    } finally {
      setIsSendingFriendRequest(false)
    }
  }

  const acceptFriendRequest = async () => {
    if (!friendRequest) {
      return
    }

    setIsAcceptingFriendRequest(true)
    setError(null)
    setSocialResult(null)

    try {
      const response = await fetch(`/api/v1/friend-requests/${friendRequest.id}/acceptance`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ recipientUserId: learnerUserId }),
      })

      if (!response.ok) {
        throw new Error(`Friend Request accept failed with ${response.status}`)
      }

      const accepted: FriendRequest = await response.json()
      setFriendRequest(accepted)
      setSocialResult('Friend Request accepted. Users can now Direct Message.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to accept Friend Request')
    } finally {
      setIsAcceptingFriendRequest(false)
    }
  }

  const declineFriendRequest = async () => {
    if (!friendRequest) {
      return
    }

    setIsDecliningFriendRequest(true)
    setError(null)
    setSocialResult(null)

    try {
      const response = await fetch(`/api/v1/friend-requests/${friendRequest.id}/decline`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ recipientUserId: learnerUserId }),
      })

      if (!response.ok) {
        throw new Error(`Friend Request decline failed with ${response.status}`)
      }

      const declined: FriendRequest = await response.json()
      setFriendRequest(declined)
      setDirectMessages([])
      setSocialResult('Friend Request declined.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to decline Friend Request')
    } finally {
      setIsDecliningFriendRequest(false)
    }
  }

  const sendDirectMessage = async () => {
    setIsSendingDirectMessage(true)
    setError(null)
    setSocialResult(null)

    try {
      const response = await fetch('/api/v1/direct-messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          senderUserId: ownerUserId,
          recipientUserId: learnerUserId,
          body: directMessageBody,
        }),
      })

      if (!response.ok) {
        throw new Error(`Direct Message send failed with ${response.status}`)
      }

      await refreshDirectMessages()
      setSocialResult('Direct Message sent between friends.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to send Direct Message')
    } finally {
      setIsSendingDirectMessage(false)
    }
  }

  const verifyNonFriendCannotDirectMessage = async () => {
    setIsCheckingDirectMessageAccess(true)
    setError(null)
    setSocialResult(null)

    try {
      const response = await fetch('/api/v1/direct-messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          senderUserId: nonEnrolledUserId,
          recipientUserId: learnerUserId,
          body: 'Can we talk?',
        }),
      })

      if (response.status !== 403) {
        throw new Error(`Expected non-friend DM to fail with 403, got ${response.status}`)
      }

      setSocialResult('Non-friend Direct Message is blocked.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to verify Direct Message permissions')
    } finally {
      setIsCheckingDirectMessageAccess(false)
    }
  }

  const blockSender = async () => {
    setIsBlockingUser(true)
    setError(null)
    setSocialResult(null)

    try {
      const response = await fetch('/api/v1/user-blocks', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          blockerUserId: learnerUserId,
          blockedUserId: ownerUserId,
        }),
      })

      if (!response.ok) {
        throw new Error(`Block user failed with ${response.status}`)
      }

      setSocialResult('Learner blocked Owner from Direct Messages.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to block user')
    } finally {
      setIsBlockingUser(false)
    }
  }

  const verifyBlockedUserCannotDirectMessage = async () => {
    setIsCheckingDirectMessageAccess(true)
    setError(null)
    setSocialResult(null)

    try {
      const response = await fetch('/api/v1/direct-messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          senderUserId: ownerUserId,
          recipientUserId: learnerUserId,
          body: 'Are you there?',
        }),
      })

      if (response.status !== 403) {
        throw new Error(`Expected blocked DM to fail with 403, got ${response.status}`)
      }

      setSocialResult('Blocked user cannot send Direct Messages.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to verify block behavior')
    } finally {
      setIsCheckingDirectMessageAccess(false)
    }
  }

  return (
    <main className="app-shell">
      <aside className="workspace-panel" aria-label="Study Server setup">
        <div>
          <p className="eyebrow">Chanter</p>
          <h1>Study Servers</h1>
        </div>

        <form className="create-form" onSubmit={createStudyServer}>
          <label htmlFor="server-name">Study Server name</label>
          <input
            id="server-name"
            name="server-name"
            value={serverName}
            onChange={(event) => setServerName(event.target.value)}
            maxLength={120}
            required
          />
          <button type="submit" disabled={isCreating}>
            {isCreating ? 'Creating...' : 'Create Study Server'}
          </button>
          {error ? <p className="form-error">{error}</p> : null}
        </form>

        <div className="owner-block">
          <span>Owner</span>
          <code>{ownerUserId}</code>
          <span>Instructor</span>
          <code>{instructorUserId}</code>
          <span>Learner</span>
          <code>{learnerUserId}</code>
          <span>Non-friend</span>
          <code>{nonEnrolledUserId}</code>
        </div>

        <section className="social-summary">
          <div>
            <p className="eyebrow">Direct Messages</p>
            <h3>Friend Requests</h3>
          </div>
          <div className="social-actions">
            <button type="button" onClick={sendFriendRequest} disabled={isSendingFriendRequest}>
              {isSendingFriendRequest ? 'Sending...' : 'Send Request'}
            </button>
            <button
              type="button"
              onClick={acceptFriendRequest}
              disabled={isAcceptingFriendRequest || !friendRequest || friendRequest.status !== 'PENDING'}
            >
              {isAcceptingFriendRequest ? 'Accepting...' : 'Accept'}
            </button>
            <button
              type="button"
              onClick={declineFriendRequest}
              disabled={isDecliningFriendRequest || !friendRequest || friendRequest.status !== 'PENDING'}
            >
              {isDecliningFriendRequest ? 'Declining...' : 'Decline'}
            </button>
          </div>
          {friendRequest ? (
            <p className="social-meta">
              Request <code>{friendRequest.id}</code> is {friendRequest.status}.
            </p>
          ) : null}
          <label htmlFor="direct-message-body">Direct Message body</label>
          <input
            id="direct-message-body"
            value={directMessageBody}
            onChange={(event) => setDirectMessageBody(event.target.value)}
            maxLength={2000}
          />
          <div className="social-actions social-actions-wide">
            <button type="button" onClick={sendDirectMessage} disabled={isSendingDirectMessage}>
              {isSendingDirectMessage ? 'Sending...' : 'Send DM'}
            </button>
            <button type="button" onClick={refreshDirectMessages} disabled={isRefreshingDirectMessages}>
              {isRefreshingDirectMessages ? 'Refreshing...' : 'Refresh DMs'}
            </button>
            <button
              type="button"
              onClick={verifyNonFriendCannotDirectMessage}
              disabled={isCheckingDirectMessageAccess}
            >
              {isCheckingDirectMessageAccess ? 'Checking...' : 'Check Non-Friend'}
            </button>
          </div>
          <div className="social-actions">
            <button type="button" onClick={blockSender} disabled={isBlockingUser}>
              {isBlockingUser ? 'Blocking...' : 'Block Owner'}
            </button>
            <button
              type="button"
              onClick={verifyBlockedUserCannotDirectMessage}
              disabled={isCheckingDirectMessageAccess}
            >
              {isCheckingDirectMessageAccess ? 'Checking...' : 'Check Blocked DM'}
            </button>
          </div>
          {socialResult ? <p className="system-line">{socialResult}</p> : null}
          <div className="direct-message-list" aria-label="Direct Messages">
            {directMessages.length > 0 ? (
              directMessages.map((message) => (
                <div className="direct-message-row" key={message.id}>
                  <code>{message.senderUserId}</code>
                  <span>{message.body}</span>
                </div>
              ))
            ) : (
              <p>No Direct Messages yet.</p>
            )}
          </div>
        </section>
      </aside>

      <section className="server-surface" aria-live="polite">
        {studyServer ? (
          <>
            <header className="server-header">
              <div>
                <p className="eyebrow">Created Study Server</p>
                <h2>{studyServer.name}</h2>
              </div>
              <span className="role-pill">{studyServer.ownerRole.role.replaceAll('_', ' ')}</span>
            </header>

            <div className="channel-layout">
              <nav className="channel-list" aria-label="Default channels">
                <section>
                  <h3>Text Channels</h3>
                  {textChannels.map((channel) => (
                    <a href={`#${channel.id}`} key={channel.id}>
                      <span>#</span>
                      {channel.name}
                    </a>
                  ))}
                </section>
                <section>
                  <h3>Voice Channels</h3>
                  {voiceChannels.map((channel) => (
                    <a href={`#${channel.id}`} key={channel.id}>
                      <span>&gt;</span>
                      {channel.name}
                    </a>
                  ))}
                </section>
                {course ? (
                  <section>
                    <h3>Course Channels</h3>
                    {course.channels.map((channel) => (
                      <a href={`#${channel.id}`} key={channel.id}>
                        <span>#</span>
                        {channel.name}
                      </a>
                    ))}
                  </section>
                ) : null}
              </nav>

              <article className="conversation-pane">
                <p className="system-line">Welcome to {studyServer.name}</p>
                <p>
                  The Study Server shell is ready with default Study Server Channels and the creator
                  assigned as Owner.
                </p>
                <form className="course-form" onSubmit={createCourseAndCohort}>
                  <label htmlFor="course-title">Course title</label>
                  <input
                    id="course-title"
                    value={courseTitle}
                    onChange={(event) => setCourseTitle(event.target.value)}
                    maxLength={160}
                    required
                  />
                  <label htmlFor="cohort-name">Cohort name</label>
                  <input
                    id="cohort-name"
                    value={cohortName}
                    onChange={(event) => setCohortName(event.target.value)}
                    maxLength={120}
                    required
                  />
                  <button type="submit" disabled={isCreatingCourse}>
                    {isCreatingCourse ? 'Creating...' : 'Create Course + Cohort'}
                  </button>
                </form>
                {course ? (
                  <section className="course-summary">
                    <div>
                      <p className="eyebrow">Created Course</p>
                      <h3>{course.title}</h3>
                      <p>{course.cohort.name}</p>
                    </div>
                    <button type="button" onClick={enrollLearner} disabled={isEnrolling}>
                      {isEnrolling ? 'Enrolling...' : 'Enroll Learner'}
                    </button>
                    {accessResult ? <p className="system-line">{accessResult}</p> : null}
                  </section>
                ) : null}
                {selectedVoiceChannel ? (
                  <section className="voice-summary">
                    <div>
                      <p className="eyebrow">Voice Channel</p>
                      <h3>{selectedVoiceChannel.name}</h3>
                    </div>
                    <div className="voice-actions">
                      <button type="button" onClick={joinVoiceChannel} disabled={isJoiningVoice}>
                        {isJoiningVoice ? 'Joining...' : 'Join'}
                      </button>
                      <button
                        type="button"
                        onClick={verifyNonMemberCannotJoinVoice}
                        disabled={isCheckingVoiceAccess}
                      >
                        {isCheckingVoiceAccess ? 'Checking...' : 'Check Non-Member'}
                      </button>
                      <button type="button" onClick={leaveVoiceChannel} disabled={isLeavingVoice}>
                        {isLeavingVoice ? 'Leaving...' : 'Leave'}
                      </button>
                    </div>
                    {voiceResult ? <p className="system-line">{voiceResult}</p> : null}
                    <div className="voice-presence-list" aria-label="Voice Channel presence">
                      {voicePresences.length > 0 ? (
                        voicePresences.map((presence) => (
                          <div className="voice-presence-row" key={presence.memberUserId}>
                            <code>{presence.memberUserId}</code>
                            <span>{presence.canSpeak ? 'Speak' : 'Muted'}</span>
                            <span>{presence.canListen ? 'Listen' : 'Deafened'}</span>
                          </div>
                        ))
                      ) : (
                        <p>No one is in voice.</p>
                      )}
                    </div>
                  </section>
                ) : null}
              </article>
            </div>
          </>
        ) : (
          <div className="empty-state">
            <p className="eyebrow">Issue #12</p>
            <h2>Create a Study Server</h2>
            <p>Use the setup panel to create the first learning community shell.</p>
          </div>
        )}
      </section>

      <aside className="status-panel" aria-label="Service status">
        <h2>Services</h2>
        <StatusRow label="Gateway" value={health.gateway} />
        <StatusRow label="Auth" value={health.auth} />
        <StatusRow label="Community" value={health.community} />
        <StatusRow label="Message" value={health.message} />
      </aside>
    </main>
  )
}

function StatusRow({ label, value }: { label: string; value: string }) {
  const displayValue = String(value)

  return (
    <div className="status-row">
      <span>{label}</span>
      <strong className={`status ${displayValue.toLowerCase()}`}>{displayValue}</strong>
    </div>
  )
}

export default App
