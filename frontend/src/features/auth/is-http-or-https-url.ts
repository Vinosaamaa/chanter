/**
 * Returns true when {@code value} is an absolute http(s) URL (SEC-22).
 * Rejects javascript:, data:, and other non-http schemes before rendering into href.
 */
export function isHttpOrHttpsUrl(value: string | null | undefined): boolean {
  if (value == null || value.trim() === '') {
    return false
  }
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}
