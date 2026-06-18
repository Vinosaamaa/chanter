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

type SupportQuestion = {
  id: string
  channelId: string
  senderUserId: string
  body: string
  status: string
  idempotencyKey?: string
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

const DEMO_USERS_STORAGE_KEY = 'chanter-demo-user-ids'

type DemoUserIds = {
  owner: string
  instructor: string
  learner: string
  nonEnrolled: string
}

type FriendshipStatus = 'NONE' | 'PENDING' | 'ACCEPTED' | 'DECLINED'

const loadDemoUserIds = (): DemoUserIds => {
  try {
    const raw = sessionStorage.getItem(DEMO_USERS_STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<DemoUserIds>
      if (parsed.owner && parsed.instructor && parsed.learner && parsed.nonEnrolled) {
        return {
          owner: parsed.owner,
          instructor: parsed.instructor,
          learner: parsed.learner,
          nonEnrolled: parsed.nonEnrolled,
        }
      }
    }
  } catch {
    // Fall through to fresh demo identities.
  }

  const ids = {
    owner: createUserId(),
    instructor: createUserId(),
    learner: createUserId(),
    nonEnrolled: createUserId(),
  }
  sessionStorage.setItem(DEMO_USERS_STORAGE_KEY, JSON.stringify(ids))
  return ids
}

const truncateId = (id: string) => `${id.slice(0, 8)}…`

function App() {
  const [health, setHealth] = useState<HealthState>({
    gateway: 'checking',
    auth: 'checking',
    community: 'checking',
    message: 'checking',
  })
  const [demoUserIds] = useState(loadDemoUserIds)
  const ownerUserId = demoUserIds.owner
  const instructorUserId = demoUserIds.instructor
  const learnerUserId = demoUserIds.learner
  const nonEnrolledUserId = demoUserIds.nonEnrolled
  const [friendshipStatus, setFriendshipStatus] = useState<FriendshipStatus>('NONE')
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
  const [isRemovingFriendship, setIsRemovingFriendship] = useState(false)
  const [socialResult, setSocialResult] = useState<string | null>(null)
  const [socialError, setSocialError] = useState<string | null>(null)
  const [supportQuestionBody, setSupportQuestionBody] = useState('How do I configure Spring Security?')
  const [supportQuestions, setSupportQuestions] = useState<SupportQuestion[]>([])
  const [isPostingSupportQuestion, setIsPostingSupportQuestion] = useState(false)
  const [isListingSupportQuestions, setIsListingSupportQuestions] = useState(false)
  const [supportQuestionResult, setSupportQuestionResult] = useState<string | null>(null)
  const [supportQuestionError, setSupportQuestionError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refreshDirectMessages = async () => {
    setIsRefreshingDirectMessages(true)
    setSocialError(null)

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
      setSocialError(caught instanceof Error ? caught.message : 'Unable to refresh Direct Messages')
      return []
    } finally {
      setIsRefreshingDirectMessages(false)
    }
  }

  useEffect(() => {
    let cancelled = false

    const loadFriendshipState = async () => {
      try {
        const response = await fetch(
          `/api/v1/friendships/status?userId=${ownerUserId}&peerUserId=${learnerUserId}`,
        )

        if (!response.ok || cancelled) {
          return
        }

        const data: {
          status: FriendshipStatus
          friendRequestId: string | null
          senderUserId: string | null
          recipientUserId: string | null
        } = await response.json()

        if (cancelled) {
          return
        }

        setFriendshipStatus(data.status === 'DECLINED' ? 'NONE' : data.status)

        if (data.friendRequestId && data.senderUserId && data.recipientUserId && data.status !== 'NONE') {
          setFriendRequest({
            id: data.friendRequestId,
            senderUserId: data.senderUserId,
            recipientUserId: data.recipientUserId,
            status: data.status,
          })
        } else {
          setFriendRequest(null)
        }

        if (data.status === 'ACCEPTED') {
          const messagesResponse = await fetch(
            `/api/v1/direct-messages?viewerUserId=${learnerUserId}&peerUserId=${ownerUserId}`,
          )
          if (messagesResponse.ok && !cancelled) {
            const messagesData: { messages: DirectMessage[] } = await messagesResponse.json()
            setDirectMessages(messagesData.messages)
          }
        }
      } catch {
        // Demo status sync is best-effort on load.
      }
    }

    void loadFriendshipState()

    return () => {
      cancelled = true
    }
  }, [ownerUserId, learnerUserId])

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

  const postSupportQuestion = async () => {
    if (!questionsChannel) {
      return
    }

    setIsPostingSupportQuestion(true)
    setSupportQuestionError(null)
    setSupportQuestionResult(null)

    try {
      const idempotencyKey = crypto.randomUUID()
      const response = await fetch(
        `/api/v1/course-channels/${questionsChannel.id}/support-questions`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            senderUserId: learnerUserId,
            body: supportQuestionBody,
            idempotencyKey,
          }),
        },
      )

      if (!response.ok) {
        throw new Error(`Support Question post failed with ${response.status}`)
      }

      const created: SupportQuestion = await response.json()
      setSupportQuestions((current) => {
        if (current.some((question) => question.id === created.id)) {
          return current
        }
        return [...current, created]
      })
      setSupportQuestionResult('Learner posted an unanswered Support Question in #questions.')
    } catch (caught) {
      setSupportQuestionError(
        caught instanceof Error ? caught.message : 'Unable to post Support Question',
      )
    } finally {
      setIsPostingSupportQuestion(false)
    }
  }

  const listUnansweredSupportQuestions = async () => {
    if (!questionsChannel) {
      return
    }

    setIsListingSupportQuestions(true)
    setSupportQuestionError(null)
    setSupportQuestionResult(null)

    try {
      const response = await fetch(
        `/api/v1/course-channels/${questionsChannel.id}/support-questions?viewerUserId=${instructorUserId}`,
      )

      if (!response.ok) {
        throw new Error(`Support Question list failed with ${response.status}`)
      }

      const data: { supportQuestions: SupportQuestion[] } = await response.json()
      setSupportQuestions(data.supportQuestions)
      setSupportQuestionResult(`Instructor sees ${data.supportQuestions.length} unanswered Support Question(s).`)
    } catch (caught) {
      setSupportQuestionError(
        caught instanceof Error ? caught.message : 'Unable to list Support Questions',
      )
    } finally {
      setIsListingSupportQuestions(false)
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

  const sendFriendRequest = async () => {
    setIsSendingFriendRequest(true)
    setSocialError(null)
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

      if (response.status === 409) {
        setSocialError('Already friends or a request is pending. Remove friendship first if you want to re-test.')
        return
      }

      if (!response.ok) {
        throw new Error(`Friend Request failed with ${response.status}`)
      }

      const created: FriendRequest = await response.json()
      setFriendRequest(created)
      setFriendshipStatus('PENDING')
      setSocialResult(`Friend Request sent (${created.status}).`)
      setHealth((current) => ({ ...current, message: 'ok' }))
    } catch (caught) {
      setSocialError(caught instanceof Error ? caught.message : 'Unable to send Friend Request')
    } finally {
      setIsSendingFriendRequest(false)
    }
  }

  const acceptFriendRequest = async () => {
    if (!friendRequest) {
      return
    }

    setIsAcceptingFriendRequest(true)
    setSocialError(null)
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
      setFriendshipStatus('ACCEPTED')
      setSocialResult('Friend Request accepted. Users can now Direct Message.')
    } catch (caught) {
      setSocialError(caught instanceof Error ? caught.message : 'Unable to accept Friend Request')
    } finally {
      setIsAcceptingFriendRequest(false)
    }
  }

  const declineFriendRequest = async () => {
    if (!friendRequest) {
      return
    }

    setIsDecliningFriendRequest(true)
    setSocialError(null)
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
      setFriendshipStatus('NONE')
      setDirectMessages([])
      setSocialResult('Friend Request declined.')
    } catch (caught) {
      setSocialError(caught instanceof Error ? caught.message : 'Unable to decline Friend Request')
    } finally {
      setIsDecliningFriendRequest(false)
    }
  }

  const removeFriendship = async () => {
    setIsRemovingFriendship(true)
    setSocialError(null)
    setSocialResult(null)

    try {
      const response = await fetch('/api/v1/friendships/removal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          requesterUserId: ownerUserId,
          friendUserId: learnerUserId,
        }),
      })

      if (!response.ok) {
        throw new Error(`Remove friendship failed with ${response.status}`)
      }

      setFriendRequest(null)
      setFriendshipStatus('NONE')
      setDirectMessages([])
      setSocialResult('Friendship removed. Send a new request to become friends again.')
    } catch (caught) {
      setSocialError(caught instanceof Error ? caught.message : 'Unable to remove friendship')
    } finally {
      setIsRemovingFriendship(false)
    }
  }

  const sendDirectMessage = async () => {
    setIsSendingDirectMessage(true)
    setSocialError(null)
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
      setSocialResult('Direct Message sent. Inbox refreshed as Learner.')
    } catch (caught) {
      setSocialError(caught instanceof Error ? caught.message : 'Unable to send Direct Message')
    } finally {
      setIsSendingDirectMessage(false)
    }
  }

  const verifyNonFriendCannotDirectMessage = async () => {
    setIsCheckingDirectMessageAccess(true)
    setSocialError(null)
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
      setSocialError(
        caught instanceof Error ? caught.message : 'Unable to verify Direct Message permissions',
      )
    } finally {
      setIsCheckingDirectMessageAccess(false)
    }
  }

  const blockSender = async () => {
    setIsBlockingUser(true)
    setSocialError(null)
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
      setSocialError(caught instanceof Error ? caught.message : 'Unable to block user')
    } finally {
      setIsBlockingUser(false)
    }
  }

  const verifyBlockedUserCannotDirectMessage = async () => {
    setIsCheckingDirectMessageAccess(true)
    setSocialError(null)
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
      setSocialError(caught instanceof Error ? caught.message : 'Unable to verify block behavior')
    } finally {
      setIsCheckingDirectMessageAccess(false)
    }
  }

  const demoUsers = [
    { label: 'Owner', id: ownerUserId },
    { label: 'Instructor', id: instructorUserId },
    { label: 'Learner', id: learnerUserId },
    { label: 'Non-friend', id: nonEnrolledUserId },
  ]

  const canSendFriendRequest = friendshipStatus === 'NONE'
  const canSendDirectMessage = friendshipStatus === 'ACCEPTED'
  const questionsChannel = course?.channels.find((channel) => channel.name === 'questions') ?? null

  return (
    <main className="app-shell">
      <aside className="workspace-panel" aria-label="Study Server setup">
        <header className="sidebar-header">
          <p className="eyebrow">Chanter</p>
          <h1>Study Servers</h1>
          <p className="sidebar-lead">Developer demo — simulates multiple users in one browser until login ships (#30).</p>
        </header>

        <div className="sidebar-scroll">
          <section className="panel-card">
            <h2 className="panel-title">Create Study Server</h2>
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
              <button type="submit" className="btn btn-primary" disabled={isCreating}>
                {isCreating ? 'Creating...' : 'Create Study Server'}
              </button>
              {error ? <p className="form-error">{error}</p> : null}
            </form>
          </section>

          <section className="panel-card panel-card--callout">
            <h2 className="panel-title">No login yet — demo mode</h2>
            <p className="demo-copy">
              This page is a <strong>developer test harness</strong>, not the real product. There is no sign-up,
              sign-in, or second person in another browser. One tab pretends to be several users so we can test the
              backend APIs before auth (#30) and the Discord-like friends UI (#31).
            </p>
            <ol className="demo-flow">
              <li>
                <span className="flow-step">1</span>
                <span>
                  <strong>Send request</strong> — Owner asks Learner to be friends.
                </span>
              </li>
              <li>
                <span className="flow-step">2</span>
                <span>
                  <strong>Accept</strong> — you play Learner in the same tab and accept.
                </span>
              </li>
              <li>
                <span className="flow-step">3</span>
                <span>
                  <strong>Send DM</strong> — Owner messages Learner; <strong>Refresh</strong> loads the thread as
                  Learner.
                </span>
              </li>
            </ol>
            <p className="demo-copy demo-copy--muted">
              To test with a real second person you would need two accounts, two browsers, and login — planned for
              later issues.
            </p>
          </section>

          <section className="panel-card panel-card--compact">
            <h2 className="panel-title">Simulated users (this browser)</h2>
            <p className="demo-copy demo-copy--muted">
              Demo user IDs stay the same for this browser tab (session storage) so refresh does not break your test.
            </p>
            <ul className="identity-list">
              {demoUsers.map((user) => (
                <li key={user.label}>
                  <span>{user.label}</span>
                  <code title={user.id}>{truncateId(user.id)}</code>
                </li>
              ))}
            </ul>
          </section>

          <section className="panel-card">
            <div className="panel-heading">
              <p className="eyebrow">Issue #15 API test</p>
              <h2 className="panel-title">Friends &amp; DMs</h2>
            </div>

            {socialError ? <p className="form-error">{socialError}</p> : null}
            {socialResult ? <p className="system-line">{socialResult}</p> : null}

            <p className="meta-line">
              Friendship status: <span className="status-chip">{friendshipStatus}</span>
            </p>

            <div className="subsection">
              <h3 className="subsection-title">Friend request</h3>
              <p className="action-hint">Owner → Learner</p>
              <div className="button-grid">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={sendFriendRequest}
                  disabled={isSendingFriendRequest || !canSendFriendRequest}
                  title={canSendFriendRequest ? undefined : 'Remove friendship first if you want to send again'}
                >
                  {isSendingFriendRequest ? 'Sending...' : 'Send (as Owner)'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={acceptFriendRequest}
                  disabled={isAcceptingFriendRequest || !friendRequest || friendRequest.status !== 'PENDING'}
                >
                  {isAcceptingFriendRequest ? 'Accepting...' : 'Accept (as Learner)'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={declineFriendRequest}
                  disabled={isDecliningFriendRequest || !friendRequest || friendRequest.status !== 'PENDING'}
                >
                  {isDecliningFriendRequest ? 'Declining...' : 'Decline (as Learner)'}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={removeFriendship}
                  disabled={isRemovingFriendship || friendshipStatus !== 'ACCEPTED'}
                >
                  {isRemovingFriendship ? 'Removing...' : 'Remove friend'}
                </button>
              </div>
              {friendRequest ? (
                <p className="meta-line">
                  Request <code title={friendRequest.id}>{truncateId(friendRequest.id)}</code> is{' '}
                  <span className="status-chip">{friendRequest.status}</span>
                </p>
              ) : null}
            </div>

            <div className="subsection">
              <h3 className="subsection-title">Direct message</h3>
              <p className="action-hint">Owner → Learner (after accept)</p>
              <label htmlFor="direct-message-body">Message</label>
              <textarea
                id="direct-message-body"
                className="message-input"
                value={directMessageBody}
                onChange={(event) => setDirectMessageBody(event.target.value)}
                maxLength={2000}
                rows={3}
              />
              <div className="button-grid">
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={sendDirectMessage}
                  disabled={isSendingDirectMessage || !canSendDirectMessage}
                >
                  {isSendingDirectMessage ? 'Sending...' : 'Send (as Owner)'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={refreshDirectMessages}
                  disabled={isRefreshingDirectMessages || !canSendDirectMessage}
                >
                  {isRefreshingDirectMessages ? 'Refreshing...' : 'Refresh (as Learner)'}
                </button>
              </div>
            </div>

            <div className="subsection">
              <h3 className="subsection-title">Guards</h3>
              <p className="action-hint">Negative tests with Non-friend &amp; block rules</p>
              <div className="button-grid">
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={verifyNonFriendCannotDirectMessage}
                  disabled={isCheckingDirectMessageAccess}
                >
                  {isCheckingDirectMessageAccess ? 'Checking...' : 'Non-friend block'}
                </button>
                <button type="button" className="btn btn-ghost" onClick={blockSender} disabled={isBlockingUser}>
                  {isBlockingUser ? 'Blocking...' : 'Block owner'}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={verifyBlockedUserCannotDirectMessage}
                  disabled={isCheckingDirectMessageAccess}
                >
                  {isCheckingDirectMessageAccess ? 'Checking...' : 'Blocked DM'}
                </button>
              </div>
            </div>

            <div className="direct-message-list" aria-label="Direct Messages">
              <h3 className="subsection-title">Inbox</h3>
              {directMessages.length > 0 ? (
                directMessages.map((message) => (
                  <div className="direct-message-row" key={message.id}>
                    <code title={message.senderUserId}>{truncateId(message.senderUserId)}</code>
                    <span>{message.body}</span>
                  </div>
                ))
              ) : (
                <p className="empty-copy">No direct messages yet.</p>
              )}
            </div>
          </section>
        </div>
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
                    {accessResult && questionsChannel ? (
                      <section className="support-question-summary">
                        <p className="eyebrow">Support Questions (#16)</p>
                        <label htmlFor="support-question-body">Learner question in #{questionsChannel.name}</label>
                        <textarea
                          id="support-question-body"
                          value={supportQuestionBody}
                          onChange={(event) => setSupportQuestionBody(event.target.value)}
                          rows={3}
                        />
                        <div className="voice-actions">
                          <button
                            type="button"
                            onClick={postSupportQuestion}
                            disabled={isPostingSupportQuestion}
                          >
                            {isPostingSupportQuestion ? 'Posting...' : 'Post as Learner'}
                          </button>
                          <button
                            type="button"
                            onClick={listUnansweredSupportQuestions}
                            disabled={isListingSupportQuestions}
                          >
                            {isListingSupportQuestions ? 'Loading...' : 'List unanswered (Instructor)'}
                          </button>
                        </div>
                        {supportQuestionResult ? <p className="system-line">{supportQuestionResult}</p> : null}
                        {supportQuestionError ? <p className="form-error">{supportQuestionError}</p> : null}
                        {supportQuestions.length > 0 ? (
                          <ul className="support-question-list">
                            {supportQuestions.map((question) => (
                              <li key={question.id}>
                                <strong>{question.status}</strong> — {question.body}
                              </li>
                            ))}
                          </ul>
                        ) : null}
                      </section>
                    ) : null}
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
                            <code title={presence.memberUserId}>{truncateId(presence.memberUserId)}</code>
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
