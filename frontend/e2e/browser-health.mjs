/** Classify only known cancellation cases, never a missing or failed API response. */
export function isExpectedRequestAbort(isNavigation, errorText, responseStatus, documentReplaced = false) {
  return /^(?:net::ERR_ABORTED|NS_BINDING_ABORTED|Load request cancelled)$/.test(errorText)
    && !(responseStatus >= 400)
    && (isNavigation || responseStatus === 204 || documentReplaced)
}

/** Retain old-document requests until a particular navigation actually commits. */
export function navigationAbortTracker() {
  const pending = new Set()
  const replaced = new WeakSet()
  let candidate
  return {
    started(request, mainNavigation, redirectedFrom) {
      if (mainNavigation) {
        const previous = new Set(pending)
        if (candidate && candidate.request === redirectedFrom) for (const item of candidate.previous) previous.add(item)
        candidate = { request, previous }
      } else if (candidate) {
        candidate.previous.add(request)
      }
      pending.add(request)
    },
    finished(request) { pending.delete(request) },
    failed(request) {
      pending.delete(request)
      if (candidate?.request === request) candidate = undefined
    },
    candidate() { return candidate?.request },
    committed(request) {
      if (candidate?.request !== request) return
      for (const previous of candidate.previous) replaced.add(previous)
      candidate = undefined
    },
    wasReplaced(request) { return replaced.has(request) },
  }
}
