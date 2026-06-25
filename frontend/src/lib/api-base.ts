export function getApiBase(): string {
  return import.meta.env.VITE_API_BASE ?? import.meta.env.VITE_API_BASE_URL ?? ''
}
