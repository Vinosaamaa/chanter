#!/usr/bin/env bash
set -euo pipefail

# Requires GitHub Pro (or a public repository) for branch protection on private repos.
# See docs/operations/project-operations-bootstrap.md

owner="${1:-Vinosaamaa}"
repo="${2:-chanter}"

gh api --method PUT "repos/${owner}/${repo}/branches/main/protection" --input - <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["backend", "frontend"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF

echo "Branch protection enabled for ${owner}/${repo}:main"
