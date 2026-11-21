// Pickers used to ask for "?size=100" and silently drop everyone after the hundredth guard. This reads every page.
import { apiClient } from "../../api/client"

export const PAGE_SIZE = 200
/** A safety stop: 50 pages of 200 is 10 000 rows, far past any picker. */
export const MAX_PAGES = 50

/**
 * Reads all pages of a Spring page endpoint. Returns the same shape the pickers already unwrap
 * ({ data: { data: { content } } }), so call sites change by one line.
 */
export async function fetchAllPages(path: string) {
  const items: unknown[] = []
  for (let page = 0; page < MAX_PAGES; page++) {
    const sep = path.includes("?") ? "&" : "?"
    const r = await apiClient.get(`${path}${sep}page=${page}&size=${PAGE_SIZE}`)
    const body = r.data?.data ?? r.data
    if (Array.isArray(body)) { items.push(...body); break } // not paged: that is everything
    const content: unknown[] = body?.content ?? []
    items.push(...content)
    if (body?.last !== false || content.length === 0) break
  }
  return { data: { data: { content: items } } }
}

export const getAllGuards = () => fetchAllPages("/api/v1/security/guards")
export const getAllSites = () => fetchAllPages("/api/v1/security/sites")
