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
No endpoint, role, deletion or billing behavior changes.
