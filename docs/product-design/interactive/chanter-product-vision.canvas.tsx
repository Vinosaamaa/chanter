import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Grid,
  H1,
  H2,
  H3,
  Pill,
  Row,
  Stack,
  Text,
  useCanvasState,
  useHostTheme,
} from 'cursor/canvas'

type ScreenId =
  | 'landing'
  | 'auth'
  | 'servers'
  | 'shell'
  | 'questions'
  | 'voice'
  | 'friends'
  | 'dashboard'
  | 'later'

type Screen = {
  id: ScreenId
  title: string
  role: string
  summary: string
  layout: string[]
  actions: { label: string; target: ScreenId; story: string }[]
}

const SCREENS: Screen[] = [
  {
    id: 'landing',
    title: 'Marketing home',
    role: 'Visitor',
    summary:
      'Public site for educators. Explains why Chanter beats Discord for cohort-based learning operations.',
    layout: [
      'Hero: Discord for learning communities + AI teaching assistants',
      'CTAs: Create Study Server, View demo, Pricing',
      'Feature bands: channels, AI assistant, TA queue, instructor dashboard',
    ],
    actions: [
      { label: 'Sign in', target: 'auth', story: 'Educator creates a global account or returns.' },
      { label: 'View demo', target: 'shell', story: 'Sandbox Study Server without billing.' },
    ],
  },
  {
    id: 'auth',
    title: 'Sign in & onboarding',
    role: 'New or returning user',
    summary: 'One global identity. Roles are layered per Study Server, Course, and Cohort.',
    layout: [
      'Email / SSO sign-in (issue #30)',
      'Invite link to join a Study Server',
      'Or enroll in a cohort after instructor adds you',
    ],
    actions: [
      { label: 'Enter Study Server', target: 'servers', story: 'Land on server picker after auth.' },
    ],
  },
  {
    id: 'servers',
    title: 'Study Server home',
    role: 'Owner, Instructor, Learner',
    summary: 'Pick a community. Each Study Server holds courses, channels, and members.',
    layout: [
      'Left rail: your Study Server icons',
      'Main: courses & cohorts you can access',
      'Owner: create course, assign instructors',
      'Top bar: Friends hub, notifications, profile',
    ],
    actions: [
      {
        label: 'Open Spring Boot Cohort',
        target: 'shell',
        story: 'Opens the Discord-like 3-column shell for that server.',
      },
      { label: 'Friends', target: 'friends', story: 'Platform-wide social layer, not course support.' },
    ],
  },
  {
    id: 'shell',
    title: 'Study Server shell',
    role: 'All members',
    summary: 'Core app — familiar Discord layout with education-specific context panel.',
    layout: [
      'Col 1: server switcher',
      'Col 2: #announcements #general (server) + course channels + voice',
      'Col 3: realtime chat & threads',
      'Col 4: AI assistant, resources, queue (context-aware)',
    ],
    actions: [
      {
        label: '#questions',
        target: 'questions',
        story: 'Learner asks; AI answers from approved resources; TA picks up if unsure.',
      },
      {
        label: '> study-room',
        target: 'voice',
        story: 'Join voice presence; Office Hours reuses this transport.',
      },
      { label: 'Friends', target: 'friends', story: 'DMs and friend requests live here.' },
      {
        label: 'Instructor dashboard',
        target: 'dashboard',
        story: 'Ops view — not reading every message manually.',
      },
    ],
  },
  {
    id: 'questions',
    title: '#questions course channel',
    role: 'Learner, Instructor, TA',
    summary: 'Primary learning-support surface. Support Questions are tracked workflows.',
    layout: [
      'Chat: learner posts Support Question',
      'AI Study Assistant replies with citations (#19)',
      'Low confidence → Add to TA Queue (#21)',
      'Instructor lists unanswered; promotes repeated Qs to FAQ (#20)',
    ],
    actions: [
      { label: 'Back to shell', target: 'shell', story: 'Return to channel list.' },
      { label: 'TA queue', target: 'dashboard', story: 'TA picks up async items.' },
    ],
  },
  {
    id: 'voice',
    title: 'Voice channel',
    role: 'Study Server member',
    summary: 'Realtime audio like Discord. MVP uses presence; WebRTC/LiveKit in realtime slice.',
    layout: [
      'Who is in the room + speaking indicators',
      'Join / leave controls',
      'Office Hours: scheduled cohort window (#22)',
    ],
    actions: [{ label: 'Back to shell', target: 'shell', story: 'Leave voice, return to channels.' }],
  },
  {
    id: 'friends',
    title: 'Friends hub',
    role: 'Any user',
    summary: 'Social layer separate from course workflows. REST today; live WS in #31.',
    layout: [
      'Friend requests: send, accept, decline (#15 done)',
      'Friends list with online presence (#31)',
      'Live DM conversation panel (#31)',
      'DM voice call between friends (#32)',
    ],
    actions: [{ label: 'Back to shell', target: 'shell', story: 'Return to Study Server.' }],
  },
  {
    id: 'dashboard',
    title: 'Instructor dashboard',
    role: 'Instructor, Owner',
    summary: 'Buyer-facing value — visibility without reading every channel.',
    layout: [
      'Unanswered & repeated Support Questions',
      'FAQ candidates awaiting approval',
      'TA Queue load + Office Hours schedule',
      'Channel summaries + AI usage vs plan quota (#23–#24)',
    ],
    actions: [
      { label: 'Open #questions', target: 'questions', story: 'Drill into a hot topic.' },
      { label: 'Back to shell', target: 'shell', story: 'Return to teaching context.' },
    ],
  },
  {
    id: 'later',
    title: 'Later phases',
    role: 'Instructor, Learner',
    summary: 'Post-MVP expansion after the education wedge is trusted.',
    layout: [
      'Course storefront & paid enrollment in-server',
      'Built-in Live Class video + recordings',
      'Agent marketplace & voice agents',
      'Organization SSO & verified educator badge',
    ],
    actions: [{ label: 'Back to MVP shell', target: 'shell', story: 'Focus stays on #12–#24 first.' }],
  },
]

const screenById = Object.fromEntries(SCREENS.map((s) => [s.id, s])) as Record<ScreenId, Screen>

const BUILT = [
  '#12 Study Server + default channels',
  '#13 Course, cohort, enrollment',
  '#14 Voice presence',
  '#15 Friend requests & DMs (API)',
  '#16 Support questions in #questions',
]

const NEXT = [
  '#17 Course resources upload',
  '#18–#19 AI Study Assistant + grounded answers',
  '#20–#21 FAQs + TA queue routing',
  '#22 Office Hours',
  '#23–#24 Instructor dashboard + SaaS limits',
]

function Wireframe({ lines, accent }: { lines: string[]; accent: string }) {
  const theme = useHostTheme()
  const panel = { background: theme.bg.elevated, borderRadius: 4, padding: 6 }
  return (
    <div
      style={{
        border: `1px solid ${theme.stroke.secondary}`,
        borderRadius: 8,
        background: theme.bg.chrome,
        padding: 12,
        minHeight: 220,
        display: 'grid',
        gridTemplateColumns: '56px 140px 1fr 160px',
        gap: 8,
        fontSize: 11,
        color: theme.text.secondary,
      }}
    >
      <div style={panel}>SRV</div>
      <div style={panel}>
        {lines.slice(0, 4).map((l) => (
          <div key={l} style={{ marginBottom: 4, color: l.includes('#') ? accent : theme.text.tertiary }}>
            {l}
          </div>
        ))}
      </div>
      <div style={{ ...panel, padding: 8, color: theme.text.primary }}>
        {lines[4] ?? 'Main content area'}
      </div>
      <div style={panel}>Panel</div>
    </div>
  )
}

export default function ChanterProductVision() {
  const theme = useHostTheme()
  const [screenId, setScreenId] = useCanvasState<ScreenId>('screen', 'landing')
  const [lastStory, setLastStory] = useCanvasState<string | null>('story', null)
  const screen = screenById[screenId]

  const wireLines: Record<ScreenId, string[]> = {
    landing: ['', '', '', '', 'Hero + feature cards + pricing'],
    auth: ['', '', '', '', 'Sign-in form + invite code'],
    servers: ['CS', 'SB', 'PY', 'Course cards + create'],
    shell: ['#ann', '#gen', '#q', '>voice', 'Chat + context panel'],
    questions: ['#ann', '#gen', '#q', '>voice', 'Support Q + AI citations'],
    voice: ['#ann', '#gen', '#q', '>voice', 'Voice grid + participants'],
    friends: ['Friends', 'Online', 'DMs', 'Requests'],
    dashboard: ['Metrics', 'Queue', 'FAQs', 'AI usage'],
    later: ['Store', 'Live', 'Agents', 'Revenue'],
  }

  return (
    <Stack gap={16}>
      <Stack gap={4}>
        <H1>Chanter — final product vision</H1>
        <Text tone="secondary">
          Click actions below to walk through screens. Solid MVP (#12–#24); dashed boxes are later.
        </Text>
      </Stack>

      <Row gap={12} wrap>
        <Pill active={screenId === 'landing'}>Visitor path</Pill>
        <Pill active={screenId === 'shell'}>Core app</Pill>
        <Pill active={screenId === 'dashboard'}>Instructor ops</Pill>
        <Pill active={screenId === 'later'}>Later</Pill>
      </Row>

      <Grid columns={2} gap={16}>
        <Card>
          <CardHeader trailing={<Pill active>{screen.role}</Pill>}>{screen.title}</CardHeader>
          <CardBody>
            <Stack gap={12}>
              <Text>{screen.summary}</Text>
              <Wireframe lines={wireLines[screenId]} accent={theme.accent.primary} />
              <Stack gap={6}>
                <H3>On this screen</H3>
                {screen.layout.map((line) => (
                  <Text key={line} tone="secondary">
                    {line}
                  </Text>
                ))}
              </Stack>
              {lastStory ? (
                <Card variant="borderless">
                  <CardBody>
                    <Text>
                      <Text weight="semibold">User story: </Text>
                      {lastStory}
                    </Text>
                  </CardBody>
                </Card>
              ) : null}
              <Row gap={8} wrap>
                {screen.actions.map((action) => (
                  <Button
                    key={action.label}
                    onClick={() => {
                      setLastStory(action.story)
                      setScreenId(action.target)
                    }}
                  >
                    {action.label}
                  </Button>
                ))}
                {screenId !== 'later' ? (
                  <Button variant="ghost" onClick={() => setScreenId('later')}>
                    See later phases
                  </Button>
                ) : null}
              </Row>
            </Stack>
          </CardBody>
        </Card>

        <Stack gap={12}>
          <Card>
            <CardHeader>Built on main today</CardHeader>
            <CardBody>
              <Stack gap={6}>
                {BUILT.map((item) => (
                  <Text key={item}>{item}</Text>
                ))}
              </Stack>
            </CardBody>
          </Card>
          <Card>
            <CardHeader>Next MVP slices</CardHeader>
            <CardBody>
              <Stack gap={6}>
                {NEXT.map((item) => (
                  <Text key={item} tone="secondary">
                    {item}
                  </Text>
                ))}
              </Stack>
            </CardBody>
          </Card>
          <Card variant="borderless">
            <CardHeader>Jump to any screen</CardHeader>
            <CardBody>
              <Row gap={6} wrap>
                {SCREENS.map((s) => (
                  <Button key={s.id} variant="ghost" onClick={() => setScreenId(s.id)}>
                    {s.title}
                  </Button>
                ))}
              </Row>
            </CardBody>
          </Card>
        </Stack>
      </Grid>
    </Stack>
  )
}
