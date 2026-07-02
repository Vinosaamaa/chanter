# Issue #61 — CodeRabbit fix log (PR #78)

## Pass 1

| Comment | Action |
|---------|--------|
| LiveKit SDK version behind | Fixed — `0.13.0` |
| `setTtl(900)` wrong unit (ms vs minutes) | Fixed — `Duration.ofMinutes(15).toMillis()` |
| Office hours access before window check | Fixed — reorder + inline presence save |
| `issueVoiceChannelMediaToken` duplicate join | Fixed — `saveVoicePresence` only |
| LiveKit properties validation | Fixed — `@Validated` + `@NotBlank` |
| Dev credentials in `application.yml` | Fixed — require env vars |
| Redundant channel lookup | Fixed — `findStudyChannel` once |
| Office hours voice missing `key` | Fixed — `key={session.id}` |
| Voice error missing `aria-live` | Fixed |
| `VoiceStatusBadge` loose typing | Fixed — `VoiceConnectionStatus` |
| Room leak on mic failure | Fixed — disconnect room on mic error |
| Disconnect cleanup unhandled rejection | Fixed — try/catch in `disconnect` |
| Leave order (backend before client) | Fixed — `leaveVoiceChannel` before `disconnect` |
| Stale presence list | Fixed — 10s poll while connected |

## Deferred

None.
