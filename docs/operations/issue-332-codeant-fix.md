# Issue #332 review dispositions

The completed full CodeAnt review at `d8fa5f65` reported no inline findings. Both native recovery architectures passed encrypted PostgreSQL restore, the actual encrypted terminal prefix fixture and bounded localhost helper transport. That checkpoint predates journal version 2 and worker-isolation integration; it does not approve later changes or complete whole-application recovery.

Independent implementation review identified two blockers to actual source startup. Historical images ignore a new recovery flag, so the isolated composition now requires an explicit release capability before starting any service. Its build policy remains disabled until all participant handlers and native isolation proof are accepted. Ordinary background jobs must also remain absent while restored authority is incomplete. A common recovery-mode condition omits worker beans, including separated native expiry scheduling, while leaving repositories and private handlers available.

The shared #251 protocol now requires version 2 and the explicit moderation-record preservation policy in every entry and digest. Missing policy and version 1 fail closed. The fixed reference digest matches the updated actual Java implementation. Manifest and source-receipt schemas remain independently versioned.

Full review and exact-head gates must repeat after these changes and subsequent accepted source integration. Provider freshness, retained objects and original-writer fencing remain explicit open acceptance, and public cutover remains disabled.
