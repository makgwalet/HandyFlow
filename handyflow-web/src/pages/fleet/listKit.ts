// Shared behaviour for the Fleet lists: search across a row's text and show 25 rows at a time.
// Used by every list that can grow past a screenful, so they all behave the same.
import { useState } from "react"

export const DRIVER_PAGE_SIZE = 25
export interface Paged<T> { rows: T[]; page: number; pages: number; from: number; to: number; total: number }
/** One page of a list. The page number is clamped, so a page that no longer exists (after a filter) shows the last real one. */
export function paginate<T>(items: T[], page: number, size = DRIVER_PAGE_SIZE): Paged<T> {
  const pages = Math.max(1, Math.ceil(items.length / size)), at = Math.max(0, Math.min(page, pages - 1)), start = at * size
  return { rows: items.slice(start, start + size), page: at, pages, from: items.length === 0 ? 0 : start + 1, to: Math.min(items.length, start + size), total: items.length }
}

/** Every word of the search must appear in the row's text (any case). An empty search keeps every row. */
export function searchRows<T>(rows: T[], query: string, text: (row: T) => string): T[] {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean)
  return words.length === 0 ? rows : rows.filter(r => { const t = text(r).toLowerCase(); return words.every(w => t.includes(w)) })
}

/** Search plus paging for a list that is already loaded. Changing the search goes back to the first page. */
export function useListView<T>(rows: T[], text: (row: T) => string, size = DRIVER_PAGE_SIZE) {
  const [search, setSearchText] = useState(""), [page, setPage] = useState(0)
  const shown = searchRows(rows, search, text)
  return { search, setSearch: (q: string) => { setSearchText(q); setPage(0) }, setPage, shown, paged: paginate(shown, page, size) }
}
