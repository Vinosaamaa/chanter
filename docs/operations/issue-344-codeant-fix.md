# Issue 344 review disposition

Round 1: CodeAnt comment 4078820167 confirmed that a failed accessible-server
query removed the selected server without exposing its error or a usable retry.
Two focused regressions failed first. The hook now returns the owning list error,
retries that list, and requests only a revalidated accessible server afterward.
Teaching retains its error/retry view when navigation authority is unavailable;
cached dashboard, owner and error state never substitute for current authority.
All eleven Teaching/hook tests pass. A hosted synthetic case covers initial list
failure, no dashboard request, explicit retry and normalized accessible bookmark.

The Engineering receipt title initially omitted the PR's issue suffix. It now
matches the title exactly, and validation against the live PR identity/body with
the candidate commit passes. No policy exception or check suppression was added.

Exact final-head review and hosted checks remain required.
