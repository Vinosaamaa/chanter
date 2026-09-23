# Issue 344 implementation

Base: accepted main `634b9dd1`. One issue-scoped baseline lane, child of #339.
Extracted the audio accumulator/client, three Node regressions and stable receiver
assertion from `c58db625`. Extracted only the mature Teaching/dashboard redirect,
server query/loading behavior, associated tests, route relocation and three
summary CSS rules. No account/privacy features or bundle-cap changes.

The extracted Teaching tests first failed four cases on the baseline, then all
nine passed. Three audio counter tests pass. The preserved #251 hosted log
establishes its 1,452-byte overage. Lint/build and hosted results are recorded in
the system review. Both pass with unchanged budgets after reducing only redundant
summary typography/shell selectors. No local product or browser server was started.

The hosted fixture adds twelve targeted cases across three engines and three sizes.
It checks the old bookmark, summary, unchanged query, Refresh loading without
cached counts, unavailable state, retry and screenshots. Actual audio proof stays
in the existing product suite. The first nine cases passed on hosted checkpoint
2c8a56ec, and representative phone/landscape/desktop images were inspected. The
list-authority error case and final source correction require fresh hosted proof.
Integration and release proof remain open.
