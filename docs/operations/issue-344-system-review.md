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
core JavaScript 1263.8 KiB raw and CSS 214.8 KiB raw; all unchanged limits pass.
The first extraction exceeded CSS by 73 bytes. The same three summary rules now
inherit label size/weight and use their unique component class without redundant
shell prefixes. No existing global rule or budget changed.
Hosted responsive pixels, exact-head CI/product audio and full CodeAnt review
remain pending. No local server or browser run is claimed. Root owns merge.
