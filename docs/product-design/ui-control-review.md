# UI control and evidence review

This is the active #254 audit, not a completion certificate. Fixture browser runs render production components against an explicit synthetic server. They do not establish backend behavior.

| Route family | Current control behavior | Evidence and remaining gate |
| --- | --- | --- |
| Public landing | Register/sign-in links, product/use-case navigation | Actual 390/768/1280 images reviewed; expanded widths and browser smoke pending |
| Sign-in, verification, recovery | Real cookie session bootstrap, registration/verification, OAuth start, recovery and sign-out retry | #242 merged real journeys; rerun against reconstructed exact head |
| Shell and Home | Real Course links, unread count, mobile Browse, skip link, schedule actions, retry/new-account states | Six-width fixture images; drawer focus/Escape and Home reflow passed |
| Account devices | Real session list and revocation, native modal, explicit 15-minute remaining-access disclosure | Auth component regressions inherited from #242; light dialog screenshots/landscape review pending |
| Course overview | Real cohort switching, sourced activity and schedule links, absent progress represented as unavailable | Phone/desktop images passed; clipped tabs now have scroll controls |
| Course chat | Real channels and realtime conversation; permission/error states; message-only composer | Portrait/landscape composer visibility passed in Chromium fixture; real delivery and calls require backend journeys |
| Questions | Phone list/detail/back, staff replies/moderation, AI action, citation excerpts and resource links | Author overlap and phone queue fixed; model/mode/cost and persisted audit integration depend on #248; assistant chips still require control review |
| Resources | Permission-gated upload, preview/download, search and type filters | Layout rendered; processing/scanning/deletion lifecycle waits for #244 integration |
| People and Course administration | Real roster, invitation/enrollment and staff management controls | Existing API hooks retained; owner/table/dialog screenshot expansion and #251 capability proof pending |
| Community announcements | Real announcement author/body/reactions; owner editing and invitation | Layout rendered; modal focus and mutation journeys still required |
| Community lounge | Real messages/profiles/timestamps, empty/loading/error/reconnect states | Production sample data and inert voice/attachment/emoji buttons removed; focused regressions passed; new screenshots pending |
| Community events and discovery | Event RSVP/share/calendar, catalog filter/enrollment | Layout rendered; full interaction proof and wide/mobile states pending |
| Friends and direct messages | Real relationships, text messages and voice call lifecycle | Phone conversation layout rendered; focus return and reconnect/call/permission cases still under review |
| Inbox | Filter/read/done/source route actions; phone detail/back | Synthetic Mark done + return-to-list passed; real mutation journey required on final head |
| Calendar | Real date/filter/search/RSVP; named date grid with arrow, Home/End and Page navigation | Invalid ARIA grid found and corrected through a failing then passing regression; new axe and browser results pending |
| Teaching | Real Course queues, FAQ review, Office Hours and usage actions | Large empty priority cards compacted after actual review; expanded screenshots pending |
| Billing | Real current quota/plan draft and save; honest absence of paid subscriptions/invoices | Fake settings controls removed; sourced usage retained; real plan behavior waits for #250 |
| Onboarding, legal and legacy management routes | Real join/create forms and legal text | Initial onboarding/legal screenshots exist; complete management-route reconstruction and dialog auditing remain open |

## Release gates still open

- Every role's visible control must have a successful action, truthful disabled reason, or useful recovery state. Inventory rows are not assertions that all controls passed.
- Browser images at 360/390, 768, 1280, 1920 and 3840px, portrait/landscape, long content, new accounts and error states require final review.
- Automated WCAG 2.2 checks supplement keyboard, screen-reader and actual browser-zoom verification. Density-adjusted viewport tests are only equivalent reflow evidence.
- Chromium, Firefox and WebKit must exercise the real signed-in core journey on the final merged dependency set.
- Route import budgets measure compressed assets. Production-like network performance and p75 Core Web Vitals require separate observation.
- #254 stays open until its dependency, review, merged-main and release conditions are met.
