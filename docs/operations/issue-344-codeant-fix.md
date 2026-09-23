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

Round 2: the actual hosted initial-outage fixture exposed a nested observer mount
loop. The shell hid Teaching content during loading; mounting its list observer
after failure started a new automatic retry. A real QueryClient parent/child
regression reproduced the failure. Only the nested dashboard observer now opts
out of `retryOnMount` and `refetchOnMount`. The shared hook's default options stay
unchanged. Explicit Retry and a changed account still fetch current authority;
all twelve focused checks pass. The existing hosted failing case remains intact.

Comment 4078858879 suggests closed-peer getStats can reject. This is unconfirmed:
the no-selector loop predates the extraction, and actual hosted product audio
passed at both 2c8a56ec and 5a130186. The [W3C statistics model](https://www.w3.org/TR/webrtc/#statistics-model)
supports observations after close; its no-selector algorithm has no close-state
rejection. An actual observation failure remains a test failure. A broad catch or
frozen successful result would weaken evidence, so no source change was made.
