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

/**
 * Every row of a paged list, by reading page after page until the server says there are no more (or `maxPages` is reached, which is reported).
 * A plain array or an object without paging info counts as a single page, so older endpoints and test data still work.
 */
export async function fetchAllPages<T>(get: (url: string) => Promise<{ data: unknown }>, base: string, pageSize = 200, maxPages = 10): Promise<{ items: T[]; truncated: boolean }> {
  const items: T[] = []
  for (let page = 0; page < maxPages; page++) {
    const r = await get(`${base}${base.includes("?") ? "&" : "?"}page=${page}&size=${pageSize}`)
    const o = r.data as { data?: unknown } | null
    const p = (o && typeof o === "object" && !Array.isArray(o) && "data" in o ? o.data : r.data) as T[] | { content?: T[]; totalPages?: number } | null | undefined
    const chunk = (Array.isArray(p) ? p : p?.content) ?? []
    items.push(...chunk)
    const pages = Array.isArray(p) ? undefined : p?.totalPages
    if (!pages || page + 1 >= pages || chunk.length === 0) return { items, truncated: false }
  }
  return { items, truncated: true }
}
