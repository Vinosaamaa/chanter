# Baseline CI prerequisite

Issue #344 is a child of #339, extracted before the broader product integration.
The lifecycle and recovery branches need ordinary CI evidence before that final
integration can be accepted. Open/closed issue search found #339 as the broader
owner, #241 as closed historical work and #243 as infrastructure scope. A narrow
child allows this prerequisite to land without unfinished account/privacy UI.

WebRTC removes closed RTP reports from `getStats`. The moderation test previously
summed only the current reports and could mistake disappearing observations for
changed received bytes. Retain each peer/report's latest cumulative audio counters
for this short-lived test client. Repeated observations replace counters, different
peers remain distinct and video is ignored. The real test still requires actual
received bytes/energy, participant removal and stable reception afterward. It now
also verifies the receiver remains connected with no remote participants.

Teaching and the legacy instructor dashboard read the same dashboard API. Preserve
the legacy URL and query through a redirect into Teaching's existing V2 shell.
Keep Refresh, six operational counts and lifetime free-beta usage. Resolve the
requested server against accessible servers before querying; hide prior dashboard
data while the current request loads. Office Hours distinguishes loading, failed
and genuinely empty schedules. No API, role or backend contract changes.

The visual change is a wrapping definition list, using existing typography and
colors with three scoped CSS rules. Existing teaching cards/actions remain primary.
Phone, landscape and desktop fixtures exercise the actual routed components and
loading/error/retry behavior. These synthetic fixtures do not prove server access.

All core, initial and deferred bundle limits remain unchanged. The exact #251
32c78b09 hosted log reports 1,301,452 raw JavaScript bytes against 1,300,000. This
is the recorded failing source baseline, not a claim that unchanged main failed.
The duplicate page removal provides headroom without moving shared dependencies
or unfinished features outside the budget. Exact source branches must rebase and
rebuild; this prerequisite cannot establish their final union result.

Rollback restores only test evidence handling and the two existing dashboard
presentations. There is no schema, production runtime or release-epoch change.
