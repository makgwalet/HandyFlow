// Shared reading of the API envelope for the older Fleet screens: a list from either `{ data: [...] }` or `{ data: { content: [...] } }`, and a readable
// message from a failed request. Typed so the screens do not need `any`.
export const unwrapList = <T,>(r: { data: unknown }): T[] => {
  const o = r.data as { data?: unknown } | null
  const p = (o && typeof o === "object" && !Array.isArray(o) && "data" in o ? o.data : r.data) as T[] | { content?: T[] } | null | undefined
  return (Array.isArray(p) ? p : p?.content) ?? []
}
interface ApiFailure { response?: { data?: { message?: string; errors?: Record<string, string> } } }
/** The server's message for a failed request, or the fallback. */
export const errorMessage = (e: unknown, fallback: string): string => (e as ApiFailure)?.response?.data?.message ?? fallback
/** Per-field messages when the server sent them (validation), else null. */
export const fieldErrorsOf = (e: unknown): Record<string, string> | null => (e as ApiFailure)?.response?.data?.errors ?? null
