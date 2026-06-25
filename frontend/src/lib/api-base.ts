export function getApiBase(): string {
  // Dev server should use the Vite proxy (relative /api) unless VITE_API_BASE is set explicitly.
  if (import.meta.env.DEV) {
    return import.meta.env.VITE_API_BASE ?? ''
  }

  return import.meta.env.VITE_API_BASE ?? import.meta.env.VITE_API_BASE_URL ?? ''
}
