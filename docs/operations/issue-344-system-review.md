# Issue 344 system review

Scope review: no backend, dependency, permission, migration, budget or account UI
changes. The dashboard API remains authoritative; inaccessible bookmarks are
replaced before issuing the dashboard request. Current-request identity hides
prior counts. Existing course/cohort actions and owner navigation remain intact.

Audio review: peer and report identity prevent double counting. Retained counters
preserve observed bytes after RTP report removal; they do not fabricate packets.
The actual moderation assertion retains energy/byte and stable reception checks.

Local evidence: four baseline Teaching failures reproduced; all nine extracted
Teaching tests and three audio regressions pass. Production lint/build pass with
core JavaScript 1264.0 KiB raw and CSS 214.8 KiB raw after the server-list correction;
all unchanged limits pass. Two additional red-to-green tests cover the owning
list error/retry and the rendered initial failure without redirecting to Home.
The first extraction exceeded CSS by 73 bytes. The same three summary rules now
inherit label size/weight and use their unique component class without redundant
shell prefixes. No existing global rule or budget changed.
Hosted responsive pixels, exact-head CI/product audio and full CodeAnt review
remain pending. No local server or browser run is claimed. Root owns merge.

At 5a130186 all ordinary CI jobs, including real product audio, passed; both
architectures passed on the earlier 2c8a56ec source. The new initial list-outage
fixture failed in all engines and exposed a real nested observer retry loop.
Its QueryClient regression is now red-to-green, including explicit retry and
account change. Only the nested observer changes its mount retry behavior.
The twelve-case final hosted Teaching proof and final native gates remain pending.
