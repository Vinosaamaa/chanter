/** Classify only known cancellation cases, never a missing or failed API response. */
export function isExpectedRequestAbort(isNavigation, errorText, responseStatus) {
  return /ERR_ABORTED|NS_BINDING_ABORTED/.test(errorText)
    && (isNavigation || responseStatus === 204)
}
