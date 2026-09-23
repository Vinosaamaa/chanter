# Responsive enrollment management

Repository: Vinosaamaa/chanter. Owner: #339 / PR340. Lane: root product
interaction, registered issue-339 worktree, branch codex/339-product-interaction.
The actual owner journey exposed enrollment still using the retired app shell.
Move its existing route into the responsive shell without changing enrollment
authority, cohort selection, API calls or custom course-channel destinations.

Use the repository frontend-design skill and learning-desk-v3 tokens: white paper
#FFFFFF, desk #F3F6FA, ink #192C46, quiet ink #596A80, blue #2458D3 and rule #DCE3EC.
Instrument Sans remains the interface font. Course title and cohort establish
context; the roster is the main working column, with invitation and access details
in a bounded secondary column. Manual enrollment stays beside the roster it updates.
At narrow widths those sections become one scrolling column inside the mobile
shell. Do not shrink the desktop navigation into the phone viewport.

This is enrollment administration, not a metrics dashboard. No decorative counters,
new cards, invented learner names or new onboarding steps. Preserve the actual
learner identifier and enrollment time. Error and success text use the established
readable colors. Inputs use 16px text and controls provide 44px touch targets.
Long course titles and invitation URLs wrap; the roster may scroll inside its own
bounded region without making the whole page overflow.

Acceptance: existing capability, cohort, search and pagination regressions pass;
real owner enrollment and invitation journeys pass with the responsive account
menu; three-engine phone, landscape and desktop fixtures prove shell, input size,
roster/error/retry rendering, invitation copy failure and manual-enrollment results.
Actual clipboard permission remains a separate real-browser/device limitation.
No management endpoint, role, deletion or billing behavior changes.

## Course creation access

The real owner journey reached a valid invitation but received HTTP 403. Existing
OPEN cohorts require Study Server membership; an invitation must not bypass that
boundary. Backend prerequisite #346 / PR347 adds an optional creation policy and
preserves OPEN for callers that omit it. The owner creation form explicitly sends
INVITE_ONLY by default. A native "Who can join" select also offers "Study Server
members" for OPEN. Owners can understand the distinction before creating a cohort;
there is no hidden membership change or post-creation policy mutation.

Keep the choice in the existing creation form, spanning both form columns above
the submit action. Use the same readable 16px, 44px controls on phone, landscape
and desktop. Simplify redundant card wrappers and share the identical control
class string to retain existing production bundle limits. Form tests cover both
choices; browser fixtures inspect the select and submitted payload. Actual
outsider joining and repeated PostgreSQL joining still require the accepted
backend prerequisite and final composed release.
